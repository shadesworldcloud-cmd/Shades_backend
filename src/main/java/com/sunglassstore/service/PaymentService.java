package com.sunglassstore.service;

import com.sunglassstore.dto.request.PaymentRequest;
import com.sunglassstore.entity.Payment;
import com.sunglassstore.dto.response.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {
    Payment processPayment(Long userId, Long orderId, PaymentRequest request);
    Page<PaymentResponse> getPayments(Long userId, Long orderId, Pageable pageable);
}
