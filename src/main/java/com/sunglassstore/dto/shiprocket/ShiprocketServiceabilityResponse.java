package com.sunglassstore.dto.shiprocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShiprocketServiceabilityResponse(
        @JsonProperty("status") int status,
        @JsonProperty("data") ServiceabilityData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceabilityData(
            @JsonProperty("available_courier_companies") List<CourierCompany> availableCourierCompanies
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourierCompany(
            @JsonProperty("courier_company_id") int courierCompanyId,
            @JsonProperty("courier_name") String courierName,
            @JsonProperty("rate") BigDecimal rate,
            @JsonProperty("etd") String etd,
            @JsonProperty("estimated_delivery_days") int estimatedDeliveryDays
    ) {}
}
