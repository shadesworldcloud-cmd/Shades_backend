package com.sunglassstore.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class CreateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    @Size(max = 50)
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Coupon code may contain only letters, numbers, hyphens and underscores")
    private String couponCode;

    @Size(max = 255)
    private String description;

    @NotBlank(message = "Discount type is required")
    @Pattern(regexp = "PERCENTAGE|FIXED|PAIR_FIXED", message = "Invalid discount type")
    private String discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be positive")
    private BigDecimal discountValue;

    @NotNull(message = "Minimum order amount is required")
    @DecimalMin(value = "0.00", message = "Minimum order amount cannot be negative")
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    @DecimalMin(value = "0.01", message = "Maximum discount must be positive")
    private BigDecimal maximumDiscountAmount;

    @Min(value = 1, message = "Usage limit must be at least 1")
    private Integer usageLimit;
    @Min(value = 1, message = "Per-customer usage limit must be at least 1")
    private Integer usageLimitPerUser;

    @NotNull(message = "Valid from date is required")
    private LocalDateTime validFrom;

    @NotNull(message = "Valid to date is required")
    private LocalDateTime validTo;
}
