package com.sunglassstore.email.event;

import java.time.LocalDateTime;

public record ShipmentStatusEmailRequested(String email, String customerName, Long orderId,
                                            Long shipmentId, String status, String provider,
                                            String trackingNumber, LocalDateTime expectedDeliveryAt) {
}
