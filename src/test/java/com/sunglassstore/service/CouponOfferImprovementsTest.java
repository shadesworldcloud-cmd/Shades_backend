package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateCouponRequest;
import com.sunglassstore.dto.request.ValidateCouponRequest;
import com.sunglassstore.entity.Cart;
import com.sunglassstore.entity.CartItem;
import com.sunglassstore.entity.Coupon;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.enums.CartStatus;
import com.sunglassstore.entity.enums.DiscountType;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.repository.CartRepository;
import com.sunglassstore.repository.CouponRepository;
import com.sunglassstore.repository.CouponUsageRepository;
import com.sunglassstore.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CouponOfferImprovementsTest {
    private CouponRepository coupons;
    private CouponUsageRepository usages;
    private CartRepository carts;
    private CouponServiceImpl service;

    @BeforeEach
    void setUp() {
        coupons = mock(CouponRepository.class);
        usages = mock(CouponUsageRepository.class);
        carts = mock(CartRepository.class);
        service = new CouponServiceImpl(coupons, usages, carts);
    }

    @Test
    void validationUsesAuthenticatedUsersCartInsteadOfClientTotals() {
        Coupon coupon = coupon("PAIR500", DiscountType.PAIR_FIXED, "500");
        when(coupons.findByCouponCodeIgnoreCase("PAIR500")).thenReturn(Optional.of(coupon));
        when(carts.findByUserUserIdAndCartStatus(7L, CartStatus.ACTIVE)).thenReturn(Optional.of(cart(14, "1000")));
        ValidateCouponRequest request = new ValidateCouponRequest(); request.setCouponCode("PAIR500");

        var result = service.validateCoupon(7L, request);

        assertEquals(new BigDecimal("3500.00"), result.getCalculatedDiscount());
    }

    @Test
    void updateRejectsCodeOwnedByAnotherOffer() {
        CreateCouponRequest request = request("TAKEN");
        Coupon existing = coupon("OLD", DiscountType.FIXED, "100");
        when(coupons.findById(2L)).thenReturn(Optional.of(existing));
        when(coupons.existsByCouponCodeIgnoreCaseAndCouponIdNot("TAKEN", 2L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.updateCoupon(2L, request));
        verify(coupons, never()).save(any());
    }

    @Test
    void rejectsEndDateThatDoesNotFollowStartDate() {
        CreateCouponRequest request = request("DATES");
        request.setValidTo(request.getValidFrom());
        assertThrows(BadRequestException.class, () -> service.createCoupon(request));
    }

    @Test
    void rejectsPercentageAboveOneHundred() {
        CreateCouponRequest request = request("TOOMUCH");
        request.setDiscountType("PERCENTAGE"); request.setDiscountValue(new BigDecimal("100.01"));
        assertThrows(BadRequestException.class, () -> service.createCoupon(request));
    }

    @Test
    void offerCanBeReactivated() {
        Coupon coupon = coupon("RETURN", DiscountType.FIXED, "100"); coupon.setIsActive(false);
        when(coupons.findById(3L)).thenReturn(Optional.of(coupon));
        when(coupons.save(coupon)).thenReturn(coupon);
        assertEquals(true, service.setCouponActive(3L, true).getIsActive());
    }

    private Cart cart(int quantity, String unitPrice) {
        ProductVariant variant = new ProductVariant(); variant.setPrice(new BigDecimal(unitPrice));
        CartItem item = new CartItem(); item.setVariant(variant); item.setQuantity(quantity);
        Cart cart = new Cart(); cart.setItems(List.of(item)); return cart;
    }

    private Coupon coupon(String code, DiscountType type, String value) {
        Coupon coupon = new Coupon(); coupon.setCouponId(1L); coupon.setCouponCode(code);
        coupon.setDiscountType(type); coupon.setDiscountValue(new BigDecimal(value));
        coupon.setMinimumOrderAmount(BigDecimal.ZERO); coupon.setIsActive(true);
        coupon.setValidFrom(LocalDateTime.now().minusDays(1)); coupon.setValidTo(LocalDateTime.now().plusDays(1));
        return coupon;
    }

    private CreateCouponRequest request(String code) {
        CreateCouponRequest request = new CreateCouponRequest(); request.setCouponCode(code);
        request.setDiscountType("FIXED"); request.setDiscountValue(new BigDecimal("100"));
        request.setMinimumOrderAmount(BigDecimal.ZERO); request.setValidFrom(LocalDateTime.now());
        request.setValidTo(LocalDateTime.now().plusDays(1)); return request;
    }
}
