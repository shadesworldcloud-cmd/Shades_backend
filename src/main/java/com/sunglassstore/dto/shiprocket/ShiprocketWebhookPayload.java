package com.sunglassstore.dto.shiprocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Incoming webhook payload from Shiprocket tracking updates.
 * Fields are loosely typed — Shiprocket sometimes sends numbers as strings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShiprocketWebhookPayload(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("sr_order_id") String srOrderId,
        @JsonProperty("shipment_id") String shipmentId,
        @JsonProperty("awb") String awb,
        @JsonProperty("courier_name") String courierName,
        @JsonProperty("current_status") String currentStatus,
        @JsonProperty("current_status_id") String currentStatusId,
        @JsonProperty("etd") String etd,
        @JsonProperty("scans") Object scans
) {}
