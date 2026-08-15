package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CreateRefundRequest;
import com.sunglassstore.dto.response.RefundResponse;
import com.sunglassstore.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/payments/{paymentId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<RefundResponse> processRefund(@PathVariable Long paymentId,
                                                 @Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(refundService.processRefund(paymentId, request));
    }
}
