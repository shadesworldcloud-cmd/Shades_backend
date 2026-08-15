package com.sunglassstore.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Administrator payload for creating or updating an automatic quantity offer.
 *
 * Every meaningful amount is a field: nothing about ₹500 or a group of two is compiled in. The
 * annotations here are the first line of validation and the database constraints are the second —
 * both exist because the API is not the only way rows arrive, and a group size of one would make
 * the pricing rule give away a discount per unit.
 */
@Getter
@Setter
public class AutomaticOfferRequest {

    @NotBlank(message = "Offer name is required")
    @Size(max = 120, message = "Offer name must be at most 120 characters")
    private String offerName;

    /**
     * Optional. Left null, the API generates the wording from the configured numbers, which is the
     * only way to guarantee the banner cannot contradict the calculation.
     */
    @Size(max = 300, message = "Banner message must be at most 300 characters")
    private String bannerMessage;

    @NotNull(message = "Required quantity is required")
    @Min(value = 2, message = "Required quantity must be at least 2")
    private Integer requiredQuantity;

    @NotNull(message = "Discount per group is required")
    @DecimalMin(value = "0.01", message = "Discount per group must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Discount per group must have at most 2 decimal places")
    private BigDecimal discountPerGroup;

    @DecimalMin(value = "0.00", message = "Minimum order subtotal cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Minimum order subtotal must have at most 2 decimal places")
    private BigDecimal minimumOrderSubtotal;

    /** ALL_PRODUCTS, SELECTED_PRODUCTS or SELECTED_CATEGORIES. */
    @NotBlank(message = "Eligibility scope is required")
    private String scopeType;

    private Set<Long> productIds;

    private Set<Long> categoryIds;

    @NotNull(message = "Start date and time is required")
    private LocalDateTime startsAt;

    @NotNull(message = "End date and time is required")
    private LocalDateTime endsAt;

    private Boolean isActive;

    private Integer priority;

    /**
     * The version the administrator was looking at when they opened the form. Required on update:
     * unlike the customer profile there is no older client to stay compatible with, and a silent
     * last-write-wins on a live discount is exactly the outcome the brief asks to prevent.
     */
    private Long version;
}
