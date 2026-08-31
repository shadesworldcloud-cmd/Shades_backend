package com.sunglassstore.controller;

import com.sunglassstore.dto.shiprocket.ShiprocketWebhookPayload;
import com.sunglassstore.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives tracking update webhooks from Shiprocket.
 * <p>
 * This endpoint is unauthenticated (no JWT) — Shiprocket calls it directly.
 * It is permitted in SecurityConfig. For production, validate requests using
 * a shared webhook secret or Shiprocket's IP whitelist.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class ShiprocketWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ShiprocketWebhookController.class);

    private final ShipmentService shipmentService;

    @PostMapping("/shiprocket")
    public ResponseEntity<Void> handleShiprocketWebhook(@RequestBody ShiprocketWebhookPayload payload) {
        log.info("Shiprocket webhook received: awb={}, status={}, srOrderId={}",
                payload.awb(), payload.currentStatus(), payload.srOrderId());

        try {
            shipmentService.handleWebhook(payload.awb(), payload.currentStatus());
        } catch (Exception e) {
            // Log but return 200 so Shiprocket doesn't retry indefinitely
            log.error("Error processing Shiprocket webhook for AWB {}: {}", payload.awb(), e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }
}
