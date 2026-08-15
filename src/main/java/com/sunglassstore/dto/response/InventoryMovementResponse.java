package com.sunglassstore.dto.response;

import com.sunglassstore.entity.InventoryMovement;
import java.time.LocalDateTime;

public record InventoryMovementResponse(Long inventoryMovementId, Long variantId, String sku,
                                        String movementType, Integer quantityChange,
                                        String referenceType, Long referenceId, String notes,
                                        LocalDateTime createdAt) {
    public static InventoryMovementResponse fromEntity(InventoryMovement movement) {
        return new InventoryMovementResponse(movement.getInventoryMovementId(),
                movement.getVariant().getVariantId(), movement.getVariant().getSku(),
                movement.getMovementType().name(), movement.getQuantityChange(),
                movement.getReferenceType(), movement.getReferenceId(), movement.getNotes(),
                movement.getCreatedAt());
    }
}
