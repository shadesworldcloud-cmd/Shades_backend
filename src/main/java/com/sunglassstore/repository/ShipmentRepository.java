package com.sunglassstore.repository;

import com.sunglassstore.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Page<Shipment> findByOrderOrderId(Long orderId, Pageable pageable);
    List<Shipment> findByOrderOrderIdOrderByCreatedAtDesc(Long orderId);
    boolean existsByTrackingNumberIgnoreCase(String trackingNumber);

    Optional<Shipment> findByAwbCode(String awbCode);
    Optional<Shipment> findByShiprocketOrderId(Long shiprocketOrderId);
}
