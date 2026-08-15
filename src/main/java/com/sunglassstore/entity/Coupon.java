package com.sunglassstore.entity;

import com.sunglassstore.entity.enums.DiscountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "COUPONS")
@Getter
@Setter
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COUPON_ID")
    private Long couponId;

    @Column(name = "COUPON_CODE", nullable = false, unique = true, length = 50)
    private String couponCode;

    @Column(name = "DESCRIPTION")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "DISCOUNT_TYPE", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "DISCOUNT_VALUE", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "MINIMUM_ORDER_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    @Column(name = "MAXIMUM_DISCOUNT_AMOUNT", precision = 12, scale = 2)
    private BigDecimal maximumDiscountAmount;

    @Column(name = "USAGE_LIMIT")
    private Integer usageLimit;

    @Column(name = "USAGE_LIMIT_PER_USER")
    private Integer usageLimitPerUser;

    @Column(name = "VALID_FROM", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "VALID_TO", nullable = false)
    private LocalDateTime validTo;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;
}
