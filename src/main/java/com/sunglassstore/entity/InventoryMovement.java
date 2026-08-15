package com.sunglassstore.entity;

import com.sunglassstore.entity.enums.MovementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "INVENTORY_MOVEMENTS")
@Getter
@Setter
@NoArgsConstructor
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INVENTORY_MOVEMENT_ID")
    private Long inventoryMovementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VARIANT_ID", nullable = false)
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(name = "MOVEMENT_TYPE", nullable = false, length = 30)
    private MovementType movementType;

    @Column(name = "QUANTITY_CHANGE", nullable = false)
    private Integer quantityChange;

    @Column(name = "REFERENCE_TYPE", length = 50)
    private String referenceType;

    @Column(name = "REFERENCE_ID")
    private Long referenceId;

    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
