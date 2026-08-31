package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Shipment;
import java.time.LocalDateTime;

public record ShipmentResponse(
        Long shipmentId,
        String shippingProvider,
        String trackingNumber,
        String shipmentStatus,
        LocalDateTime shippedAt,
        LocalDateTime expectedDeliveryAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt,
        // Shiprocket fields
        Long shiprocketOrderId,
        Long shiprocketShipmentId,
        String awbCode,
        String courierName,
        String labelUrl
) {
    public static ShipmentResponse fromEntity(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getShipmentId(),
                shipment.getShippingProvider(),
                shipment.getTrackingNumber(),
                shipment.getShipmentStatus().name(),
                shipment.getShippedAt(),
                shipment.getExpectedDeliveryAt(),
                shipment.getDeliveredAt(),
                shipment.getCreatedAt(),
                shipment.getShiprocketOrderId(),
                shipment.getShiprocketShipmentId(),
                shipment.getAwbCode(),
                shipment.getCourierName(),
                shipment.getLabelUrl()
        );
    }
}
