package com.sunglassstore.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateRefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be positive")
    @Digits(integer = 10, fraction = 2, message = "Refund amount must have at most 2 decimal places")
    private BigDecimal refundAmount;

    @NotNull(message = "Return ID is required")
    @Positive(message = "Return ID must be positive")
    private Long returnId;

    @NotBlank(message = "Refund reason is required")
    @Size(max = 255, message = "Refund reason cannot exceed 255 characters")
    private String reason;
}
