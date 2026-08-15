package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long paymentId, Long orderId, String paymentMethod, BigDecimal amount,
                              String status, String providerReference, LocalDateTime paidAt) {
    public static PaymentResponse fromEntity(Payment payment) {
        return new PaymentResponse(payment.getPaymentId(), payment.getOrder().getOrderId(),
                payment.getPaymentMethod(), payment.getAmount(), payment.getPaymentStatus().name(),
                payment.getProviderReference(), payment.getPaidAt());
    }
}
