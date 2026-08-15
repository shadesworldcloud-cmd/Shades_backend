package com.sunglassstore.service;

import com.sunglassstore.entity.Coupon;
import com.sunglassstore.entity.enums.DiscountType;
import com.sunglassstore.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CouponServiceImplTest {

    @Test
    void calculateDiscount_percentage() {
        CouponServiceImpl service = new CouponServiceImpl(null, null, null);
        Coupon coupon = new Coupon();
        coupon.setDiscountType(DiscountType.PERCENTAGE);
        coupon.setDiscountValue(new BigDecimal("10"));
        coupon.setMaximumDiscountAmount(new BigDecimal("100"));

        BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("500"));
        assertEquals(new BigDecimal("50.00"), discount);
    }

    @Test
    void calculateDiscount_percentage_capped() {
        CouponServiceImpl service = new CouponServiceImpl(null, null, null);
        Coupon coupon = new Coupon();
        coupon.setDiscountType(DiscountType.PERCENTAGE);
        coupon.setDiscountValue(new BigDecimal("50"));
        coupon.setMaximumDiscountAmount(new BigDecimal("100"));

        BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("500"));
        assertEquals(new BigDecimal("100.00"), discount);
    }

    @Test
    void calculateDiscount_fixed() {
        CouponServiceImpl service = new CouponServiceImpl(null, null, null);
        Coupon coupon = new Coupon();
        coupon.setDiscountType(DiscountType.FIXED);
        coupon.setDiscountValue(new BigDecimal("75"));
        coupon.setMaximumDiscountAmount(null);

        BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("500"));
        assertEquals(new BigDecimal("75.00"), discount);
    }

    @Test
    void calculateDiscount_cannotExceedOrderAmount() {
        CouponServiceImpl service = new CouponServiceImpl(null, null, null);
        Coupon coupon = new Coupon();
        coupon.setDiscountType(DiscountType.FIXED);
        coupon.setDiscountValue(new BigDecimal("200"));
        coupon.setMaximumDiscountAmount(null);

        BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("100"));
        assertEquals(new BigDecimal("100.00"), discount);
    }

    @Test
    void calculateDiscount_pairFixed_usesCompletePairsOnly() {
        CouponServiceImpl service = new CouponServiceImpl(null, null, null);
        Coupon coupon = new Coupon();
        coupon.setDiscountType(DiscountType.PAIR_FIXED);
        coupon.setDiscountValue(new BigDecimal("500"));

        assertEquals(new BigDecimal("3500.00"),
                service.calculateDiscount(coupon, new BigDecimal("10000"), 14));
        assertEquals(new BigDecimal("3500.00"),
                service.calculateDiscount(coupon, new BigDecimal("10000"), 15));
    }
}
