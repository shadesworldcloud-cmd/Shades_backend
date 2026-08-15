package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateShipmentRequest;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.Shipment;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.ShipmentStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.email.event.ShipmentStatusEmailRequested;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.repository.ShipmentRepository;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Shipment createShipment(Long orderId, CreateShipmentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.CONFIRMED &&
                order.getOrderStatus() != OrderStatus.PROCESSING) {
            throw new BadRequestException("Shipment can only be created for CONFIRMED or PROCESSING orders");
        }

        String trackingNumber = request.getTrackingNumber().trim();
        if (shipmentRepository.existsByTrackingNumberIgnoreCase(trackingNumber)) {
            throw new BadRequestException("This tracking number is already assigned to a shipment");
        }

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShippingProvider(request.getShippingProvider().trim());
        shipment.setTrackingNumber(trackingNumber);
        shipment.setExpectedDeliveryAt(request.getExpectedDeliveryAt());
        shipment.setShipmentStatus(ShipmentStatus.PENDING);

        if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
            orderService.updateOrderStatus(orderId, OrderStatus.PROCESSING, "Shipment created");
        }

        return shipmentRepository.save(shipment);
    }

    @Override
    @Transactional
    public Shipment updateShipmentStatus(Long shipmentId, ShipmentStatus status) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        ShipmentStatus oldStatus = shipment.getShipmentStatus();
        validateTransition(oldStatus, status);
        shipment.setShipmentStatus(status);

        if (status == ShipmentStatus.SHIPPED) {
            if (shipment.getShippedAt() == null) shipment.setShippedAt(LocalDateTime.now());
            syncOrderStatus(shipment.getOrder(), OrderStatus.SHIPPED, "Order shipped");
        } else if (status == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now());
            syncOrderStatus(shipment.getOrder(), OrderStatus.DELIVERED, "Order delivered");
        }

        Shipment saved = shipmentRepository.save(shipment);
        Order order = shipment.getOrder();
        eventPublisher.publishEvent(new ShipmentStatusEmailRequested(order.getUser().getEmail(),
                order.getUser().getName(), order.getOrderId(), shipment.getShipmentId(), status.name(),
                shipment.getShippingProvider(), shipment.getTrackingNumber(), shipment.getExpectedDeliveryAt()));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Shipment> getShipments(Long orderId, Pageable pageable) {
        return shipmentRepository.findByOrderOrderId(orderId, pageable);
    }

    private void syncOrderStatus(Order order, OrderStatus target, String note) {
        if (order.getOrderStatus() == target) return;
        orderService.updateOrderStatus(order.getOrderId(), target, note);
    }

    private void validateTransition(ShipmentStatus current, ShipmentStatus next) {
        if (current == next) throw new BadRequestException("Shipment is already in status " + current);
        boolean valid = switch (current) {
            case PENDING -> next == ShipmentStatus.PACKED || next == ShipmentStatus.FAILED;
            case PACKED -> next == ShipmentStatus.SHIPPED || next == ShipmentStatus.FAILED;
            case SHIPPED -> next == ShipmentStatus.IN_TRANSIT || next == ShipmentStatus.FAILED
                    || next == ShipmentStatus.RETURNED;
            case IN_TRANSIT -> next == ShipmentStatus.OUT_FOR_DELIVERY || next == ShipmentStatus.FAILED
                    || next == ShipmentStatus.RETURNED;
            case OUT_FOR_DELIVERY -> next == ShipmentStatus.DELIVERED || next == ShipmentStatus.FAILED
                    || next == ShipmentStatus.RETURNED;
            case FAILED -> next == ShipmentStatus.IN_TRANSIT || next == ShipmentStatus.OUT_FOR_DELIVERY;
            case DELIVERED -> next == ShipmentStatus.RETURNED;
            case RETURNED -> false;
        };
        if (!valid) throw new BadRequestException("Invalid shipment status transition: " + current + " to " + next);
    }
}
