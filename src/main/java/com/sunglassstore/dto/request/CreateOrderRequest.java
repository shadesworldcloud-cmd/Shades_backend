package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    @NotNull(message = "Shipping address ID is required")
    @Positive(message = "Shipping address ID must be positive")
    private Long shippingAddressId;

    @Positive(message = "Billing address ID must be positive")
    private Long billingAddressId;

    @Size(max = 50, message = "Coupon code cannot exceed 50 characters")
    private String couponCode;

    @NotNull(message = "Expected order total is required")
    @DecimalMin(value = "0.00", message = "Expected order total cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Expected order total must have at most 2 decimal places")
    private BigDecimal expectedTotalAmount;

    /**
     * Identifies one checkout attempt, generated once by the client and reused across retries.
     *
     * Optional for backwards compatibility: an older client simply gets the previous behaviour,
     * where a resubmitted checkout could place a second order. Supplied, a repeat returns the
     * original order instead — so a retried request cannot apply the offer, charge, or deduct stock
     * twice.
     */
    @Size(max = 80, message = "Idempotency key cannot exceed 80 characters")
    private String idempotencyKey;
}
