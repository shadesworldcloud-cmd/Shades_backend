package com.sunglassstore.dto.request;

import com.sunglassstore.entity.enums.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateShipmentStatusRequest {

    @NotNull(message = "Status is required")
    private ShipmentStatus status;
}
