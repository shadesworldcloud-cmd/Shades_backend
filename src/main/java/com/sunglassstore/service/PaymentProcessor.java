package com.sunglassstore.service;

import com.sunglassstore.dto.response.PaymentResult;

import java.math.BigDecimal;

/**
 * Strategy interface for payment processing.
 * Swap MockPaymentProcessor with a real provider implementation later.
 */
public interface PaymentProcessor {

    PaymentResult process(String paymentMethod, BigDecimal amount);
}
