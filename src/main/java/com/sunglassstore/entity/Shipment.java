package com.sunglassstore.entity;

import com.sunglassstore.entity.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "SHIPMENTS")
@Getter
@Setter
@NoArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SHIPMENT_ID")
    private Long shipmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    private Order order;

    @Column(name = "SHIPPING_PROVIDER", length = 100)
    private String shippingProvider;

    @Column(name = "TRACKING_NUMBER")
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "SHIPMENT_STATUS", nullable = false, length = 30)
    private ShipmentStatus shipmentStatus = ShipmentStatus.PENDING;

    @Column(name = "SHIPPED_AT")
    private LocalDateTime shippedAt;

    @Column(name = "EXPECTED_DELIVERY_AT")
    private LocalDateTime expectedDeliveryAt;

    @Column(name = "DELIVERED_AT")
    private LocalDateTime deliveredAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
