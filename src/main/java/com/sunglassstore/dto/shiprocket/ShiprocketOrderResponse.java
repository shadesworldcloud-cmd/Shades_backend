package com.sunglassstore.dto.shiprocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShiprocketOrderResponse(
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("shipment_id") Long shipmentId,
        @JsonProperty("status") String status,
        @JsonProperty("status_code") int statusCode
) {}
