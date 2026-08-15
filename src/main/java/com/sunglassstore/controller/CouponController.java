package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CreateCouponRequest;
import com.sunglassstore.dto.request.ValidateCouponRequest;
import com.sunglassstore.dto.response.CouponValidationResponse;
import com.sunglassstore.entity.Coupon;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/validate")
    public ResponseEntity<CouponValidationResponse> validateCoupon(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody ValidateCouponRequest request) {
        return ResponseEntity.ok(couponService.validateCoupon(principal.getUserId(), request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<Coupon>> getAllCoupons(Pageable pageable) {
        return ResponseEntity.ok(couponService.getAllCoupons(pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Coupon> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.createCoupon(request));
    }

    @PutMapping("/{couponId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Coupon> updateCoupon(@PathVariable Long couponId,
                                                @Valid @RequestBody CreateCouponRequest request) {
        return ResponseEntity.ok(couponService.updateCoupon(couponId, request));
    }

    @DeleteMapping("/{couponId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long couponId) {
        couponService.deleteCoupon(couponId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{couponId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Coupon> setCouponActive(@PathVariable Long couponId,
                                                   @RequestParam boolean active) {
        return ResponseEntity.ok(couponService.setCouponActive(couponId, active));
    }
}
