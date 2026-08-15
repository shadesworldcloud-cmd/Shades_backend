package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateCouponRequest;
import com.sunglassstore.dto.request.ValidateCouponRequest;
import com.sunglassstore.dto.response.CouponValidationResponse;
import com.sunglassstore.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface CouponService {

    Coupon createCoupon(CreateCouponRequest request);

    Coupon updateCoupon(Long couponId, CreateCouponRequest request);

    void deleteCoupon(Long couponId);

    Coupon setCouponActive(Long couponId, boolean active);

    Page<Coupon> getAllCoupons(Pageable pageable);

    CouponValidationResponse validateCoupon(Long userId, ValidateCouponRequest request);

    BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount);

    BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount, int itemQuantity);
}
