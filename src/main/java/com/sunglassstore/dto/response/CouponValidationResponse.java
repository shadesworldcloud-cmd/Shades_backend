package com.sunglassstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class CouponValidationResponse {

    private boolean valid;
    private String couponCode;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal calculatedDiscount;
    private String message;
}
