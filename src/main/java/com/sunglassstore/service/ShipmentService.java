package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateShipmentRequest;
import com.sunglassstore.dto.shiprocket.ShiprocketAwbResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketTrackingResponse;
import com.sunglassstore.entity.Shipment;
import com.sunglassstore.entity.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface ShipmentService {
    Shipment createShipment(Long orderId, CreateShipmentRequest request);
    Shipment updateShipmentStatus(Long shipmentId, ShipmentStatus status);
    Page<Shipment> getShipments(Long orderId, Pageable pageable);

    // ---- Shiprocket integration ----

    /** Push an existing order to Shiprocket and create a Shipment record. */
    Shipment createShiprocketShipment(Long orderId);

    /** Assign AWB (courier) to a Shiprocket shipment. */
    ShiprocketAwbResponse assignAwb(Long shipmentId);

    /** Schedule a pickup for a Shiprocket shipment. */
    Map<?, ?> schedulePickup(Long shipmentId);

    /** Generate a shipping label. */
    Map<?, ?> generateLabel(Long shipmentId);

    /** Track a shipment by its internal shipment ID. */
    ShiprocketTrackingResponse trackShipment(Long shipmentId);

    /** Handle an incoming Shiprocket webhook status update. */
    void handleWebhook(String awbCode, String currentStatus);
}
