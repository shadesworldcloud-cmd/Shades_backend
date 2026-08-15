package com.sunglassstore.service;

import com.sunglassstore.entity.InventoryMovement;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryService {
    InventoryMovement adjustInventory(Long variantId, Integer quantity, MovementType type, String reason);
    ProductVariant updateLowStockThreshold(Long variantId, Integer threshold);
    Page<InventoryMovement> getMovements(Long variantId, Pageable pageable);
}
