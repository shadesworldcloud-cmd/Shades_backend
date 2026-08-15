package com.sunglassstore.controller;

import com.sunglassstore.dto.request.PaymentRequest;
import com.sunglassstore.dto.response.PaymentResponse;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/orders/{orderId}")
    public ResponseEntity<PaymentResponse> processPayment(@AuthenticationPrincipal SecurityUser principal,
                                                    @PathVariable Long orderId,
                                                    @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PaymentResponse.fromEntity(paymentService.processPayment(principal.getUserId(), orderId, request)));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Page<PaymentResponse>> getPayments(@AuthenticationPrincipal SecurityUser principal,
                                                      @PathVariable Long orderId, Pageable pageable) {
        return ResponseEntity.ok(paymentService.getPayments(principal.getUserId(), orderId, pageable));
    }
}
