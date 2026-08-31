package com.sunglassstore.dto.shiprocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Tracking response from GET /courier/track/awb/{awb} or /courier/track/shipment/{id}.
 * The structure is flexible — Shiprocket nests data differently depending on the endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShiprocketTrackingResponse(
        @JsonProperty("tracking_data") TrackingData trackingData
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrackingData(
            @JsonProperty("track_status") int trackStatus,
            @JsonProperty("shipment_status") int shipmentStatus,
            @JsonProperty("shipment_track") List<ShipmentTrack> shipmentTrack,
            @JsonProperty("shipment_track_activities") List<TrackActivity> shipmentTrackActivities,
            @JsonProperty("track_url") String trackUrl,
            @JsonProperty("etd") String etd
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShipmentTrack(
            @JsonProperty("current_status") String currentStatus,
            @JsonProperty("delivered_date") String deliveredDate,
            @JsonProperty("pickup_date") String pickupDate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrackActivity(
            @JsonProperty("date") String date,
            @JsonProperty("status") String status,
            @JsonProperty("activity") String activity,
            @JsonProperty("location") String location
    ) {}
}
