package com.sunglassstore.dto.request;

import com.sunglassstore.entity.enums.MovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryAdjustmentRequest {
    @NotNull(message = "Quantity change is required")
    private Integer quantity;

    @NotNull(message = "Movement type is required")
    private MovementType movementType;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
