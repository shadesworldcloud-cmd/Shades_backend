package com.sunglassstore.controller;

import com.sunglassstore.config.ShiprocketConfig;
import com.sunglassstore.dto.shiprocket.ShiprocketAwbResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketServiceabilityResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketTrackingResponse;
import com.sunglassstore.dto.response.ShipmentResponse;
import com.sunglassstore.service.ShiprocketClient;
import com.sunglassstore.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    // ---- Public: delivery serviceability check ----

    /**
     * Checks whether delivery is possible to a given pincode, and returns
     * available couriers with rates and estimated delivery days.
     *
     * Pickup pincode is derived from the configured pickup location (Jaipur).
     */
    @GetMapping("/serviceability")
    public ResponseEntity<ShiprocketServiceabilityResponse> checkServiceability(
            @RequestParam("pincode") String pincode,
            @RequestParam(value = "weight", defaultValue = "0.3") double weight,
            @RequestParam(value = "cod", defaultValue = "false") boolean cod) {

        // Use the store's pincode as pickup. Jaipur Raja Park = 302004
        ShiprocketServiceabilityResponse response = shiprocketClient.checkServiceability(
                "302004", pincode, weight, cod);
        return ResponseEntity.ok(response);
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
