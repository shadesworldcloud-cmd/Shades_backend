package com.sunglassstore.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateShipmentRequest {

    @NotBlank(message = "Shipping provider is required")
    @Size(max = 100, message = "Shipping provider must be at most 100 characters")
    private String shippingProvider;

    @NotBlank(message = "Tracking number is required")
    @Size(max = 255, message = "Tracking number must be at most 255 characters")
    private String trackingNumber;

    @Future(message = "Expected delivery must be in the future")
    private LocalDateTime expectedDeliveryAt;
}
