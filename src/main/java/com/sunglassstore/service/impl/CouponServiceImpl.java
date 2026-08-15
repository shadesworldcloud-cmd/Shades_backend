package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateCouponRequest;
import com.sunglassstore.dto.request.ValidateCouponRequest;
import com.sunglassstore.dto.response.CouponValidationResponse;
import com.sunglassstore.entity.Coupon;
import com.sunglassstore.entity.Cart;
import com.sunglassstore.entity.CartItem;
import com.sunglassstore.entity.enums.DiscountType;
import com.sunglassstore.entity.enums.CartStatus;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.InvalidCouponException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.CouponRepository;
import com.sunglassstore.repository.CouponUsageRepository;
import com.sunglassstore.repository.CartRepository;
import com.sunglassstore.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final CartRepository cartRepository;

    @Override
    @Transactional
    public Coupon createCoupon(CreateCouponRequest request) {
        String normalizedCode = normalizeCode(request.getCouponCode());
        if (couponRepository.existsByCouponCodeIgnoreCase(normalizedCode)) {
            throw new ConflictException("Coupon code already exists: " + normalizedCode);
        }

        Coupon coupon = new Coupon();
        validateCampaign(request);
        mapRequestToCoupon(request, coupon);
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public Coupon updateCoupon(Long couponId, CreateCouponRequest request) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        String normalizedCode = normalizeCode(request.getCouponCode());
        if (couponRepository.existsByCouponCodeIgnoreCaseAndCouponIdNot(normalizedCode, couponId)) {
            throw new ConflictException("Coupon code already exists: " + normalizedCode);
        }
        validateCampaign(request);
        mapRequestToCoupon(request, coupon);
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        coupon.setIsActive(false);
        couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public Coupon setCouponActive(Long couponId, boolean active) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        coupon.setIsActive(active);
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Coupon> getAllCoupons(Pageable pageable) {
        return couponRepository.findAllByOrderByCouponIdDesc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponValidationResponse validateCoupon(Long userId, ValidateCouponRequest request) {
        Coupon coupon = couponRepository.findByCouponCodeIgnoreCase(request.getCouponCode())
                .orElseThrow(() -> new InvalidCouponException("Coupon not found: " + request.getCouponCode()));

        Cart cart = cartRepository.findByUserUserIdAndCartStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCouponException("Your cart is empty"));
        BigDecimal orderAmount = BigDecimal.ZERO;
        int itemQuantity = 0;
        for (CartItem item : cart.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0 || item.getVariant() == null
                    || item.getVariant().getPrice() == null) {
                throw new InvalidCouponException("Your cart contains an invalid item");
            }
            orderAmount = orderAmount.add(item.getVariant().getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity())));
            itemQuantity += item.getQuantity();
        }
        if (itemQuantity == 0 || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidCouponException("Your cart is empty");
        }

        // Check active
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            throw new InvalidCouponException("This coupon is no longer active");
        }

        // Check dates
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidTo())) {
            throw new InvalidCouponException("This coupon is not valid at this time");
        }

        // Check minimum order amount
        if (orderAmount.compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw new InvalidCouponException(
                    "Minimum order amount is " + coupon.getMinimumOrderAmount());
        }

        // Check total usage limit
        if (coupon.getUsageLimit() != null) {
            long totalUsage = couponUsageRepository.countByCouponCouponId(coupon.getCouponId());
            if (totalUsage >= coupon.getUsageLimit()) {
                throw new InvalidCouponException("This coupon has reached its usage limit");
            }
        }

        // Check per-user usage limit
        if (coupon.getUsageLimitPerUser() != null) {
            long userUsage = couponUsageRepository.countByCouponCouponIdAndUserUserId(
                    coupon.getCouponId(), userId);
            if (userUsage >= coupon.getUsageLimitPerUser()) {
                throw new InvalidCouponException("You have already used this coupon the maximum number of times");
            }
        }

        if (coupon.getDiscountType() == DiscountType.PAIR_FIXED &&
                itemQuantity < 2) {
            throw new InvalidCouponException("Add at least 2 units to use this offer");
        }

        BigDecimal discount = calculateDiscount(coupon, orderAmount, itemQuantity);

        return new CouponValidationResponse(true, coupon.getCouponCode(),
                coupon.getDiscountType().name(), coupon.getDiscountValue(),
                discount, "Coupon is valid");
    }

    @Override
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        return calculateDiscount(coupon, orderAmount, 0);
    }

    @Override
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount, int itemQuantity) {
        BigDecimal discount;

        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = orderAmount.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (coupon.getDiscountType() == DiscountType.PAIR_FIXED) {
            int completePairs = Math.max(0, itemQuantity) / 2;
            discount = coupon.getDiscountValue().multiply(BigDecimal.valueOf(completePairs));
        } else {
            discount = coupon.getDiscountValue();
        }

        // Cap at maximum discount amount if set
        if (coupon.getMaximumDiscountAmount() != null &&
                discount.compareTo(coupon.getMaximumDiscountAmount()) > 0) {
            discount = coupon.getMaximumDiscountAmount();
        }

        // Discount cannot exceed order amount
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private void mapRequestToCoupon(CreateCouponRequest request, Coupon coupon) {
        coupon.setCouponCode(normalizeCode(request.getCouponCode()));
        coupon.setDescription(request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().trim());
        coupon.setDiscountType(DiscountType.valueOf(request.getDiscountType()));
        coupon.setDiscountValue(request.getDiscountValue());
        coupon.setMinimumOrderAmount(request.getMinimumOrderAmount());
        coupon.setMaximumDiscountAmount(request.getMaximumDiscountAmount());
        coupon.setUsageLimit(request.getUsageLimit());
        coupon.setUsageLimitPerUser(request.getUsageLimitPerUser());
        coupon.setValidFrom(request.getValidFrom());
        coupon.setValidTo(request.getValidTo());
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private void validateCampaign(CreateCouponRequest request) {
        if (!request.getValidTo().isAfter(request.getValidFrom())) {
            throw new com.sunglassstore.exception.BadRequestException("Offer end date must be later than its start date");
        }
        if ("PERCENTAGE".equals(request.getDiscountType())
                && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new com.sunglassstore.exception.BadRequestException("Percentage discount cannot exceed 100%");
        }
        if (request.getUsageLimit() != null && request.getUsageLimitPerUser() != null
                && request.getUsageLimitPerUser() > request.getUsageLimit()) {
            throw new com.sunglassstore.exception.BadRequestException(
                    "Per-customer usage limit cannot exceed total usage limit");
        }
    }
}
