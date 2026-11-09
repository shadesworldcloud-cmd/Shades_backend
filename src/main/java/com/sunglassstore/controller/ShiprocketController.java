package com.sunglassstore.controller;

import com.sunglassstore.config.ShiprocketConfig;
import com.sunglassstore.dto.shiprocket.ShiprocketAwbResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketServiceabilityResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketTrackingResponse;
import com.sunglassstore.dto.response.DeliveryEstimateResponse;
import com.sunglassstore.dto.response.ShipmentResponse;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.service.DeliveryEstimateService;
import com.sunglassstore.service.ShiprocketClient;
import com.sunglassstore.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Admin endpoints for Shiprocket operations and a public serviceability check.
 */
@RestController
@RequestMapping("/api/shiprocket")
@RequiredArgsConstructor
public class ShiprocketController {

    private final ShipmentService shipmentService;
    private final ShiprocketClient shiprocketClient;
    private final ShiprocketConfig shiprocketConfig;
    private final DeliveryEstimateService deliveryEstimateService;

    private static final Pattern INDIAN_PINCODE = Pattern.compile("^[1-9][0-9]{5}$");

    // ---- Public: delivery serviceability check ----

    /**
     * Checks whether delivery is possible to a given pincode, and returns
     * available couriers with rates and estimated delivery days.
     *
     * Pickup pincode comes from {@code shiprocket.pickup-pincode}.
     */
    @GetMapping("/serviceability")
    public ResponseEntity<ShiprocketServiceabilityResponse> checkServiceability(
            @RequestParam("pincode") String pincode,
            @RequestParam(value = "weight", defaultValue = "0.3") double weight,
            @RequestParam(value = "cod", defaultValue = "false") boolean cod) {

        ShiprocketServiceabilityResponse response = shiprocketClient.checkServiceability(
                deliveryEstimateService.pickupPincode(), requirePincode(pincode), weight, cod);
        return ResponseEntity.ok(response);
    }

    /**
     * Customer-facing expected delivery dates for a pincode, used by the product page and checkout.
     * Always answers: when Shiprocket is unreachable the standard window is returned with
     * {@code source=FALLBACK} and {@code serviceable=null}.
     */
    @GetMapping("/delivery-estimate")
    public ResponseEntity<DeliveryEstimateResponse> deliveryEstimate(@RequestParam("pincode") String pincode) {
        return ResponseEntity.ok(deliveryEstimateService.estimate(requirePincode(pincode)));
    }

    private static String requirePincode(String pincode) {
        String cleaned = pincode == null ? "" : pincode.trim();
        if (!INDIAN_PINCODE.matcher(cleaned).matches()) {
            throw new BadRequestException("Enter a valid 6-digit Indian pincode");
        }
        return cleaned;
    }

    // ---- Admin: Shiprocket shipment management ----

    /**
     * Creates a Shiprocket order + local Shipment record for a confirmed order.
     */
    @PostMapping("/orders/{orderId}/ship")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<ShipmentResponse> createShiprocketShipment(@PathVariable Long orderId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ShipmentResponse.fromEntity(shipmentService.createShiprocketShipment(orderId)));
    }

    /**
     * Assigns an AWB (courier + tracking number) to a shipment.
     */
    @PostMapping("/shipments/{shipmentId}/assign-awb")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<ShiprocketAwbResponse> assignAwb(@PathVariable Long shipmentId) {
        return ResponseEntity.ok(shipmentService.assignAwb(shipmentId));
    }

    /**
     * Schedules a courier pickup for a shipment.
     */
    @PostMapping("/shipments/{shipmentId}/schedule-pickup")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<?> schedulePickup(@PathVariable Long shipmentId) {
        return ResponseEntity.ok(shipmentService.schedulePickup(shipmentId));
    }

    /**
     * Generates a shipping label PDF for a shipment.
     */
    @PostMapping("/shipments/{shipmentId}/generate-label")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<?> generateLabel(@PathVariable Long shipmentId) {
        return ResponseEntity.ok(shipmentService.generateLabel(shipmentId));
    }

    /**
     * Fetches real-time tracking data for a shipment from Shiprocket.
     */
    @GetMapping("/shipments/{shipmentId}/track")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT') or hasRole('CUSTOMER')")
    public ResponseEntity<ShiprocketTrackingResponse> trackShipment(@PathVariable Long shipmentId) {
        return ResponseEntity.ok(shipmentService.trackShipment(shipmentId));
    }
}
