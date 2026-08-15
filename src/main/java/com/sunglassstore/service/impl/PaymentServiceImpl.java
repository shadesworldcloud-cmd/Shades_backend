package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.PaymentRequest;
import com.sunglassstore.dto.response.PaymentResult;
import com.sunglassstore.dto.response.PaymentResponse;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.Payment;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.PaymentStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.repository.PaymentRepository;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.service.PaymentProcessor;
import com.sunglassstore.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.sunglassstore.notification.event.OrderPaymentConfirmed;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProcessor paymentProcessor;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Payment processPayment(Long userId, Long orderId, PaymentRequest request) {
        if (request == null || request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()) {
            throw new BadRequestException("Payment method is required");
        }
        Order order = orderRepository.findByOrderIdAndUserUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Returning the existing successful payment makes client/network retries idempotent.
        var existingPayment = paymentRepository.findFirstByOrderOrderIdAndPaymentStatus(orderId, PaymentStatus.PAID);
        if (existingPayment.isPresent()) return existingPayment.get();
        if (order.getOrderStatus() != OrderStatus.PLACED) {
            throw new BadRequestException("Payment can only be processed for orders in PLACED status");
        }

        // Process via payment processor
        String paymentMethod = request.getPaymentMethod().trim().toUpperCase();
        PaymentResult result = paymentProcessor.process(paymentMethod, order.getTotalAmount());

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(paymentMethod);
        payment.setAmount(order.getTotalAmount());
        payment.setProviderReference(result.getProviderReference());

        if (result.isSuccess()) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, "Payment received");
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
        }

        Payment saved = paymentRepository.save(payment);
        if (saved.getPaymentStatus() == PaymentStatus.PAID) {
            eventPublisher.publishEvent(new OrderPaymentConfirmed(userId, orderId, order.getUser().getName(), order.getTotalAmount()));
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPayments(Long userId, Long orderId, Pageable pageable) {
        if (!orderRepository.findByOrderIdAndUserUserId(orderId, userId).isPresent()) {
            throw new ResourceNotFoundException("Order not found");
        }
        return paymentRepository.findByOrderOrderId(orderId, pageable).map(PaymentResponse::fromEntity);
    }
}
