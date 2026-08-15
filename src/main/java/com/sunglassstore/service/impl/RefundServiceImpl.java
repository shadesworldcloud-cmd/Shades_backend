package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateRefundRequest;
import com.sunglassstore.entity.Payment;
import com.sunglassstore.entity.Refund;
import com.sunglassstore.entity.ReturnRequest;
import com.sunglassstore.entity.enums.ReturnStatus;
import com.sunglassstore.dto.response.RefundResponse;
import com.sunglassstore.entity.enums.PaymentStatus;
import com.sunglassstore.entity.enums.RefundStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.PaymentRepository;
import com.sunglassstore.repository.RefundRepository;
import com.sunglassstore.repository.ReturnRequestRepository;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.sunglassstore.email.event.RefundCompletedEmailRequested;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public RefundResponse processRefund(Long paymentId, CreateRefundRequest request) {
        validateRequest(request);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getPaymentStatus() != PaymentStatus.PAID && payment.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BadRequestException("Refunds can only be processed for paid payments");
        }

        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(request.getReturnId())
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));
        if (!returnRequest.getOrder().getOrderId().equals(payment.getOrder().getOrderId())) {
            throw new BadRequestException("Return request does not belong to this payment's order");
        }
        Long orderId = returnRequest.getOrder().getOrderId();
        orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (returnRequest.getReturnStatus() != ReturnStatus.RECEIVED && returnRequest.getReturnStatus() != ReturnStatus.COMPLETED) {
            throw new BadRequestException("Refund can be issued only after returned items are received");
        }

        // Check refund amount doesn't exceed what's available
        BigDecimal alreadyRefunded = refundRepository.sumRefundedByPaymentId(paymentId);
        if (alreadyRefunded == null) {
            alreadyRefunded = BigDecimal.ZERO;
        }

        BigDecimal paymentRemaining = payment.getAmount().subtract(alreadyRefunded);
        // Returned value net of the discount the returned units actually carried.
        //
        // ORDER_ITEMS.DISCOUNT_AMOUNT holds each line's share of the order-level discount, written
        // at order creation. Using it is what makes a partial return refund what the customer paid
        // for those units rather than their list price — and it matters most exactly where the
        // order-level ratio below would be wrong: a scoped offer discounts only some lines, so
        // returning an undiscounted line must refund its full value while returning a discounted one
        // must not. Orders placed before this column was populated carry zero, which reduces to the
        // previous gross-value behaviour.
        BigDecimal returnedNetValue = returnRequest.getItems().stream()
                .map(item -> {
                    var orderItem = item.getOrderItem();
                    BigDecimal lineDiscount = orderItem.getDiscountAmount() == null
                            ? BigDecimal.ZERO : orderItem.getDiscountAmount();
                    BigDecimal netLineValue = orderItem.getLineTotal().subtract(lineDiscount)
                            .max(BigDecimal.ZERO);
                    return netLineValue
                            .multiply(BigDecimal.valueOf(item.getQuantity()))
                            .divide(BigDecimal.valueOf(orderItem.getQuantity()), 10, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderSubtotal = returnRequest.getOrder().getSubtotalAmount();
        if (orderSubtotal == null || orderSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Order subtotal is invalid; refund cannot be calculated");
        }
        BigDecimal orderDiscount = returnRequest.getOrder().getDiscountAmount() == null
                ? BigDecimal.ZERO : returnRequest.getOrder().getDiscountAmount();
        BigDecimal netMerchandiseSubtotal = orderSubtotal.subtract(orderDiscount);
        BigDecimal shippingAmount = returnRequest.getOrder().getShippingAmount() == null
                ? BigDecimal.ZERO : returnRequest.getOrder().getShippingAmount();
        BigDecimal paidMerchandiseValue = returnRequest.getOrder().getTotalAmount().subtract(shippingAmount);
        // A wholly discounted order paid nothing for merchandise, so nothing is refundable against
        // it. Returning zero rather than dividing by zero: this is a legitimate order state, not an
        // error to reject.
        BigDecimal returnValue = netMerchandiseSubtotal.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO.setScale(2)
                : paidMerchandiseValue
                        .multiply(returnedNetValue)
                        .divide(netMerchandiseSubtotal, 2, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO);
        BigDecimal refundedForReturn = refundRepository.sumRefundedByReturnId(returnRequest.getReturnId());
        if (refundedForReturn == null) refundedForReturn = BigDecimal.ZERO;
        BigDecimal returnRemaining = returnValue.subtract(refundedForReturn);
        BigDecimal refundedForOrder = refundRepository.sumRefundedByOrderId(orderId);
        if (refundedForOrder == null) refundedForOrder = BigDecimal.ZERO;
        BigDecimal merchandiseRemaining = paidMerchandiseValue.subtract(refundedForOrder);
        BigDecimal maxRefundable = paymentRemaining.min(returnRemaining).min(merchandiseRemaining).max(BigDecimal.ZERO);
        if (request.getRefundAmount().compareTo(maxRefundable) > 0) {
            throw new BadRequestException(
                    "Refund amount exceeds available balance. Maximum refundable: " + maxRefundable);
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setReturnRequest(returnRequest);
        refund.setRefundAmount(request.getRefundAmount());
        refund.setReason(request.getReason().trim());
        refund.setRefundStatus(RefundStatus.COMPLETED); // Mock processor always succeeds
        refund.setProcessedAt(java.time.LocalDateTime.now());

        Refund savedRefund = refundRepository.save(refund);

        // Update payment status
        BigDecimal totalRefunded = alreadyRefunded.add(request.getRefundAmount());
        if (totalRefunded.compareTo(payment.getAmount()) >= 0) {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        paymentRepository.save(payment);

        eventPublisher.publishEvent(new RefundCompletedEmailRequested(
                returnRequest.getUser().getEmail(), returnRequest.getUser().getName(),
                savedRefund.getRefundId(), orderId, savedRefund.getRefundAmount()));

        return RefundResponse.fromEntity(savedRefund);
    }

    private void validateRequest(CreateRefundRequest request) {
        if (request == null || request.getReturnId() == null || request.getReturnId() <= 0) {
            throw new BadRequestException("A valid return ID is required");
        }
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund amount must be positive");
        }
        if (request.getRefundAmount().scale() > 2) {
            throw new BadRequestException("Refund amount must have at most 2 decimal places");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("Refund reason is required");
        }
    }
}
