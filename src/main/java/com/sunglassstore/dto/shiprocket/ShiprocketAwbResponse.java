package com.sunglassstore.dto.shiprocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShiprocketAwbResponse(
        @JsonProperty("response") AwbData response,
        @JsonProperty("awb_assign_status") int awbAssignStatus
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AwbData(
            @JsonProperty("data") AwbDetails data
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record AwbDetails(
                @JsonProperty("awb_code") String awbCode,
                @JsonProperty("courier_company_id") Integer courierCompanyId,
                @JsonProperty("courier_name") String courierName
        ) {}
    }
}
