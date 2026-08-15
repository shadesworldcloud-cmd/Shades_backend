package com.sunglassstore.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateLowStockThresholdRequest {
    @NotNull(message = "Low-stock threshold is required")
    @Min(value = 0, message = "Low-stock threshold cannot be negative")
    @Max(value = 1_000_000, message = "Low-stock threshold is too large")
    private Integer lowStockThreshold;
}
