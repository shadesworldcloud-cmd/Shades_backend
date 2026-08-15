package com.sunglassstore.service.impl;

import com.sunglassstore.dto.response.PaymentResult;
import com.sunglassstore.service.PaymentProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock implementation that always succeeds.
 * Replace with a real payment gateway (Stripe, Razorpay, etc.) later.
 */
@Component
public class MockPaymentProcessor implements PaymentProcessor {

    @Override
    public PaymentResult process(String paymentMethod, BigDecimal amount) {
        String reference = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResult(true, reference, "Payment processed successfully (mock)");
    }
}
