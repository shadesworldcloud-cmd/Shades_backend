package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateReturnRequest;
import com.sunglassstore.dto.response.ReturnResponse;
import com.sunglassstore.entity.*;
import com.sunglassstore.entity.enums.MovementType;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.ReturnStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.*;
import com.sunglassstore.service.ReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.sunglassstore.email.event.ReturnStatusEmailRequested;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private static final Set<String> ALLOWED_ITEM_CONDITIONS = Set.of("UNOPENED", "OPENED_UNUSED", "USED", "DAMAGED");
    private static final Set<String> SELLABLE_ITEM_CONDITIONS = Set.of("UNOPENED", "OPENED_UNUSED");

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnItemRepository returnItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ReturnResponse createReturn(Long userId, CreateReturnRequest request) {
        Order order = orderRepository.findByOrderIdAndUserUserId(request.getOrderId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Returns can only be requested for delivered orders");
        }
        if (order.getDeliveredAt() == null) {
            throw new BadRequestException("The order delivery date is not recorded");
        }
        if (order.getDeliveredAt().plusDays(30).isBefore(LocalDateTime.now())) {
            throw new BadRequestException("The 30-day return window has expired");
        }

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setOrder(order);
        returnRequest.setUser(order.getUser());
        returnRequest.setReturnStatus(ReturnStatus.REQUESTED);
        returnRequest.setReturnReason(request.getReturnReason());
        returnRequest.setCustomerComments(request.getCustomerComments());

        List<CreateReturnRequest.ReturnItemRequest> sortedRequests = new ArrayList<>(request.getItems());
        Set<Long> requestedOrderItems = new HashSet<>();
        for (CreateReturnRequest.ReturnItemRequest itemReq : sortedRequests) {
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new BadRequestException("Return quantity must be positive");
            }
            if (!requestedOrderItems.add(itemReq.getOrderItemId())) {
                throw new BadRequestException("The same order item cannot be included more than once");
            }
        }
        sortedRequests.sort(Comparator.comparing(CreateReturnRequest.ReturnItemRequest::getOrderItemId));

        List<ReturnItem> returnItems = new ArrayList<>();
        for (CreateReturnRequest.ReturnItemRequest itemReq : sortedRequests) {
            OrderItem orderItem = orderItemRepository.findByIdForUpdate(itemReq.getOrderItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Order item not found: " + itemReq.getOrderItemId()));

            // Verify the order item belongs to this order
            if (!orderItem.getOrder().getOrderId().equals(order.getOrderId())) {
                throw new BadRequestException("Order item does not belong to this order");
            }

            // Validate return quantity
            Integer alreadyReturned = returnItemRepository
                    .sumReturnedQuantityByOrderItemId(orderItem.getOrderItemId());
            int previouslyReturned = alreadyReturned != null ? alreadyReturned : 0;
            int maxReturnable = orderItem.getQuantity() - previouslyReturned;

            if (itemReq.getQuantity() > maxReturnable) {
                throw new BadRequestException(
                        "Cannot return " + itemReq.getQuantity() + " units of "
                                + orderItem.getProductName() + ". Maximum returnable: " + maxReturnable);
            }

            String itemCondition = itemReq.getItemCondition().trim().toUpperCase();
            if (!ALLOWED_ITEM_CONDITIONS.contains(itemCondition)) {
                throw new BadRequestException("Invalid item condition: " + itemReq.getItemCondition());
            }

            ReturnItem returnItem = new ReturnItem();
            returnItem.setReturnRequest(returnRequest);
            returnItem.setOrderItem(orderItem);
            returnItem.setQuantity(itemReq.getQuantity());
            returnItem.setItemCondition(itemCondition);
            returnItem.setReturnReason(itemReq.getReturnReason());
            returnItems.add(returnItem);
        }

        returnRequest.setItems(returnItems);
        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        publishReturnEmail(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnResponse> getUserReturns(Long userId, Pageable pageable) {
        return returnRequestRepository.findByUserUserIdOrderByRequestedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnResponse getReturnById(Long userId, Long returnId) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));
        if (!returnRequest.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Return request not found");
        }
        return toResponse(returnRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnResponse> getAllReturns(Pageable pageable) {
        return returnRequestRepository.findAllByOrderByRequestedAtDesc(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public ReturnResponse updateReturnStatus(Long returnId, ReturnStatus status, String adminComments,
                                               Map<Long, String> itemConditions) {
        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));

        ReturnStatus previous = returnRequest.getReturnStatus();
        validateTransition(previous, status);
        returnRequest.setReturnStatus(status);
        returnRequest.setAdminComments(adminComments == null || adminComments.isBlank() ? null : adminComments.trim());
        if (status == ReturnStatus.APPROVED && returnRequest.getApprovedAt() == null) returnRequest.setApprovedAt(LocalDateTime.now());
        if (status == ReturnStatus.RECEIVED && returnRequest.getReceivedAt() == null) returnRequest.setReceivedAt(LocalDateTime.now());
        if (status == ReturnStatus.COMPLETED && returnRequest.getCompletedAt() == null) returnRequest.setCompletedAt(LocalDateTime.now());

        if (status == ReturnStatus.RECEIVED) {
            applyInspectedConditions(returnRequest, itemConditions);
        }

        // When return is received/completed, restore inventory
        if (status == ReturnStatus.RECEIVED && previous != ReturnStatus.RECEIVED && previous != ReturnStatus.COMPLETED) {
            for (ReturnItem item : returnRequest.getItems()) {
                if (!SELLABLE_ITEM_CONDITIONS.contains(item.getItemCondition())) {
                    continue;
                }
                // The product may have been deleted since the order. There is no stock row to put
                // the unit back into, so the return still completes and is still refundable — it
                // simply restores nothing. Failing here would block a refund the customer is owed.
                if (item.getOrderItem().getVariant() == null) continue;
                ProductVariant lockedVariant = productVariantRepository.findByIdForUpdate(
                        item.getOrderItem().getVariant().getVariantId())
                        .orElseThrow(() -> new BadRequestException("Variant not found"));

                lockedVariant.setQuantityAvailable(lockedVariant.getQuantityAvailable() + item.getQuantity());
                productVariantRepository.save(lockedVariant);

                InventoryMovement movement = new InventoryMovement();
                movement.setVariant(lockedVariant);
                movement.setMovementType(MovementType.RETURN);
                movement.setQuantityChange(item.getQuantity());
                movement.setReferenceId(returnRequest.getReturnId());
                movement.setNotes("Return #" + returnRequest.getReturnId() + " received");
                inventoryMovementRepository.save(movement);
            }
        }


        if (status == ReturnStatus.COMPLETED && isEntireOrderReturned(returnRequest.getOrder())) {
            returnRequest.getOrder().setOrderStatus(OrderStatus.RETURNED);
            orderRepository.save(returnRequest.getOrder());
        }

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        publishReturnEmail(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ReturnResponse cancelReturn(Long userId, Long returnId) {
        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));
        if (!returnRequest.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Return request not found");
        }
        if (returnRequest.getReturnStatus() != ReturnStatus.REQUESTED) {
            throw new BadRequestException("Only a return awaiting review can be cancelled");
        }
        returnRequest.setReturnStatus(ReturnStatus.CANCELLED);
        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        publishReturnEmail(saved);
        return toResponse(saved);
    }

    private void publishReturnEmail(ReturnRequest request) {
        eventPublisher.publishEvent(new ReturnStatusEmailRequested(
                request.getUser().getEmail(), request.getUser().getName(), request.getReturnId(),
                request.getOrder().getOrderId(), request.getReturnStatus().name(), request.getAdminComments()));
    }

    private ReturnResponse toResponse(ReturnRequest request) {
        Long orderId = request.getOrder().getOrderId();
        return ReturnResponse.fromEntity(request,
                paymentRepository.findByOrderOrderIdOrderByCreatedAtDesc(orderId),
                refundRepository.findByReturnRequestReturnIdOrderByCreatedAtDesc(request.getReturnId()));
    }

    private boolean isEntireOrderReturned(Order order) {
        for (OrderItem orderItem : order.getItems()) {
            Integer returned = returnItemRepository.sumPhysicallyReturnedQuantityByOrderItemId(orderItem.getOrderItemId());
            if (returned == null || returned < orderItem.getQuantity()) return false;
        }
        return !order.getItems().isEmpty();
    }

    private void applyInspectedConditions(ReturnRequest returnRequest, Map<Long, String> itemConditions) {
        if (itemConditions == null) {
            throw new BadRequestException("An inspected condition is required for every received item");
        }
        for (ReturnItem item : returnRequest.getItems()) {
            String supplied = itemConditions.get(item.getReturnItemId());
            if (supplied == null || supplied.isBlank()) {
                throw new BadRequestException("Inspected condition is required for return item " + item.getReturnItemId());
            }
            String normalized = supplied.trim().toUpperCase();
            if (!ALLOWED_ITEM_CONDITIONS.contains(normalized)) {
                throw new BadRequestException("Invalid inspected condition: " + supplied);
            }
            item.setItemCondition(normalized);
        }
    }

    private void validateTransition(ReturnStatus current, ReturnStatus next) {
        if (current == next) {
            throw new BadRequestException("Return is already in status " + current);
        }
        boolean valid = switch (current) {
            case REQUESTED -> next == ReturnStatus.APPROVED || next == ReturnStatus.REJECTED || next == ReturnStatus.CANCELLED;
            case APPROVED -> next == ReturnStatus.PICKED_UP || next == ReturnStatus.CANCELLED;
            case PICKED_UP -> next == ReturnStatus.RECEIVED;
            case RECEIVED -> next == ReturnStatus.COMPLETED;
            case REJECTED, COMPLETED, CANCELLED -> false;
        };
        if (!valid) throw new BadRequestException("Invalid return status transition: " + current + " to " + next);
    }
}
