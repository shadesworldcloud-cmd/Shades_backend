package com.sunglassstore.dto.shiprocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response from Shiprocket {@code GET /courier/serviceability/}.
 * <p>
 * {@code estimated_delivery_days} is kept as a String because Shiprocket sends it as either a number
 * or a string (occasionally empty); {@link com.sunglassstore.shipping.ShiprocketDates#parseDays} reads it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShiprocketServiceabilityResponse(
        @JsonProperty("status") int status,
        @JsonProperty("data") ServiceabilityData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceabilityData(
            @JsonProperty("available_courier_companies") List<CourierCompany> availableCourierCompanies,
            @JsonProperty("recommended_courier_company_id") Integer recommendedCourierCompanyId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourierCompany(
            @JsonProperty("courier_company_id") int courierCompanyId,
            @JsonProperty("courier_name") String courierName,
            @JsonProperty("rate") BigDecimal rate,
            @JsonProperty("etd") String etd,
            @JsonProperty("estimated_delivery_days") String estimatedDeliveryDays
    ) {}
}
