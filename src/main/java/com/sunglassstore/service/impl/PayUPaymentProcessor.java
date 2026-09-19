package com.sunglassstore.service.impl;

import com.sunglassstore.dto.response.PaymentResult;
import com.sunglassstore.service.PaymentProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * PayU-aware payment processor.
 *
 * Unlike the mock, PayU payments are completed asynchronously via redirect/webhook.
 * This processor returns a PENDING result — the payment is confirmed later when
 * PayU calls back to the webhook or the user lands on the success URL.
 *
 * Marked @Primary so it replaces MockPaymentProcessor in the Spring context.
 */
@Component
@Primary
public class PayUPaymentProcessor implements PaymentProcessor {

    /**
     * For PayU, the actual charge happens on PayU's hosted page.
     * This creates a PENDING payment record; the webhook confirms it.
     */
    @Override
    public PaymentResult process(String paymentMethod, BigDecimal amount) {
        // Return pending — the real confirmation comes from PayU's callback
        return new PaymentResult(false, null, "Payment pending — awaiting PayU confirmation");
    }
}
