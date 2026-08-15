package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    private String couponCode;

}
