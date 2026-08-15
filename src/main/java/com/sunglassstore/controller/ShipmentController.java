package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CreateShipmentRequest;
import com.sunglassstore.dto.request.UpdateShipmentStatusRequest;
import com.sunglassstore.entity.Shipment;
import com.sunglassstore.dto.response.ShipmentResponse;
import com.sunglassstore.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<ShipmentResponse> createShipment(@PathVariable Long orderId,
                                                    @Valid @RequestBody CreateShipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ShipmentResponse.fromEntity(shipmentService.createShipment(orderId, request)));
    }

    @PatchMapping("/{shipmentId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<ShipmentResponse> updateStatus(@PathVariable Long shipmentId,
                                                  @Valid @RequestBody UpdateShipmentStatusRequest request) {
        return ResponseEntity.ok(ShipmentResponse.fromEntity(shipmentService.updateShipmentStatus(
                shipmentId, request.getStatus())));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<Page<ShipmentResponse>> getShipments(@PathVariable Long orderId, Pageable pageable) {
        return ResponseEntity.ok(shipmentService.getShipments(orderId, pageable).map(ShipmentResponse::fromEntity));
    }
}
