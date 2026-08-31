package com.sunglassstore.service;

import com.sunglassstore.config.ShiprocketConfig;
import com.sunglassstore.dto.shiprocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * Thin HTTP client for the Shiprocket REST API.
 * <p>
 * Authentication tokens are cached and refreshed 24 hours before expiry
 * (tokens last 10 days = 240 hours, so refresh at 9 days = 216 hours).
 */
@Service
public class ShiprocketClient {

    private static final Logger log = LoggerFactory.getLogger(ShiprocketClient.class);

    /** Shiprocket tokens are valid for 10 days; refresh at 9 days. */
    private static final long TOKEN_REFRESH_SECONDS = 9L * 24 * 60 * 60;

    private final ShiprocketConfig config;
    private final RestClient restClient;

    private String cachedToken;
    private Instant tokenObtainedAt;

    public ShiprocketClient(ShiprocketConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .build();
    }

    // ---- Authentication ----

    private synchronized String getToken() {
        if (cachedToken != null && tokenObtainedAt != null
                && Instant.now().isBefore(tokenObtainedAt.plusSeconds(TOKEN_REFRESH_SECONDS))) {
            return cachedToken;
        }
        log.info("Obtaining new Shiprocket auth token");
        ShiprocketAuthResponse auth = restClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", config.email(), "password", config.password()))
                .retrieve()
                .body(ShiprocketAuthResponse.class);

        if (auth == null || auth.token() == null) {
            throw new RuntimeException("Failed to authenticate with Shiprocket");
        }
        cachedToken = auth.token();
        tokenObtainedAt = Instant.now();
        return cachedToken;
    }

    // ---- Order ----

    /**
     * Creates an ad-hoc order on Shiprocket.
     */
    public ShiprocketOrderResponse createOrder(ShiprocketOrderRequest request) {
        return restClient.post()
                .uri("/orders/create/adhoc")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getToken())
                .body(request)
                .retrieve()
                .body(ShiprocketOrderResponse.class);
    }

    // ---- AWB & Courier ----

    /**
     * Assigns an AWB (Air Waybill) to a shipment, choosing a courier automatically.
     */
    public ShiprocketAwbResponse assignAwb(Long shiprocketShipmentId) {
        return restClient.post()
                .uri("/courier/assign/awb")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getToken())
                .body(Map.of("shipment_id", shiprocketShipmentId))
                .retrieve()
                .body(ShiprocketAwbResponse.class);
    }

    /**
     * Assigns an AWB with a specific courier.
     */
    public ShiprocketAwbResponse assignAwb(Long shiprocketShipmentId, int courierCompanyId) {
        return restClient.post()
                .uri("/courier/assign/awb")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getToken())
                .body(Map.of("shipment_id", shiprocketShipmentId, "courier_id", courierCompanyId))
                .retrieve()
                .body(ShiprocketAwbResponse.class);
    }

    // ---- Pickup ----

    /**
     * Requests a pickup for one or more shipment IDs.
     */
    public Map<?, ?> schedulePickup(Long shiprocketShipmentId) {
        return restClient.post()
                .uri("/courier/generate/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getToken())
                .body(Map.of("shipment_id", new Long[]{shiprocketShipmentId}))
                .retrieve()
                .body(Map.class);
    }

    // ---- Label ----

    /**
     * Generates a shipping label for one or more shipment IDs.
     */
    public Map<?, ?> generateLabel(Long shiprocketShipmentId) {
        return restClient.post()
                .uri("/courier/generate/label")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getToken())
                .body(Map.of("shipment_id", new Long[]{shiprocketShipmentId}))
                .retrieve()
                .body(Map.class);
    }

    // ---- Manifest ----

    public Map<?, ?> generateManifest(Long shiprocketShipmentId) {
        return restClient.post()
                .uri("/manifests/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getToken())
                .body(Map.of("shipment_id", new Long[]{shiprocketShipmentId}))
                .retrieve()
                .body(Map.class);
    }

    // ---- Tracking ----

    /**
     * Tracks a shipment by AWB code.
     */
    public ShiprocketTrackingResponse trackByAwb(String awbCode) {
        return restClient.get()
                .uri("/courier/track/awb/{awb}", awbCode)
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .body(ShiprocketTrackingResponse.class);
    }

    /**
     * Tracks a shipment by Shiprocket shipment ID.
     */
    public ShiprocketTrackingResponse trackByShipmentId(Long shipmentId) {
        return restClient.get()
                .uri("/courier/track/shipment/{id}", shipmentId)
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .body(ShiprocketTrackingResponse.class);
    }

    // ---- Serviceability ----

    /**
     * Checks which couriers can deliver to the destination pincode.
     *
     * @param pickupPincode  origin pincode (your warehouse)
     * @param deliveryPincode destination pincode (customer)
     * @param weight         weight in kg
     * @param cod            true for Cash on Delivery, false for Prepaid
     */
    public ShiprocketServiceabilityResponse checkServiceability(
            String pickupPincode, String deliveryPincode, double weight, boolean cod) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/courier/serviceability/")
                        .queryParam("pickup_postcode", pickupPincode)
                        .queryParam("delivery_postcode", deliveryPincode)
                        .queryParam("weight", weight)
                        .queryParam("cod", cod ? 1 : 0)
                        .build())
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .body(ShiprocketServiceabilityResponse.class);
    }

    // ---- Cancel ----

    /**
     * Cancels an order on Shiprocket.
     */
    public Map<?, ?> cancelOrder(Long shiprocketOrderId) {
        return restClient.post()
                .uri("/orders/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getToken())
                .body(Map.of("ids", new Long[]{shiprocketOrderId}))
                .retrieve()
                .body(Map.class);
    }
}
