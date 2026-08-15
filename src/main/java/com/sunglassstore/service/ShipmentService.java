package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateShipmentRequest;
import com.sunglassstore.entity.Shipment;
import com.sunglassstore.entity.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShipmentService {
    Shipment createShipment(Long orderId, CreateShipmentRequest request);
    Shipment updateShipmentStatus(Long shipmentId, ShipmentStatus status);
    Page<Shipment> getShipments(Long orderId, Pageable pageable);
}
