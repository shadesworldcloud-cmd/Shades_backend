package com.sunglassstore.service.impl;

import com.sunglassstore.entity.InventoryMovement;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.enums.MovementType;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.InventoryMovementRepository;
import com.sunglassstore.repository.ProductVariantRepository;
import com.sunglassstore.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final ProductVariantRepository variantRepository;
    private final InventoryMovementRepository movementRepository;

    @Override
    @Transactional
    public InventoryMovement adjustInventory(Long variantId, Integer quantity, MovementType type, String reason) {
        if (quantity == null || quantity == 0) {
            throw new BadRequestException("Quantity change must not be zero");
        }
        if (type != MovementType.PURCHASE && type != MovementType.ADJUSTMENT) {
            throw new BadRequestException("Manual inventory changes must be PURCHASE or ADJUSTMENT");
        }
        ProductVariant variant = variantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));

        long newStock = (long) variant.getQuantityAvailable() + quantity;
        if (newStock < 0) {
            throw new BadRequestException("Adjustment would result in negative stock");
        }
        if (newStock > Integer.MAX_VALUE) throw new BadRequestException("Adjustment would exceed the stock limit");

        variant.setQuantityAvailable((int) newStock);
        variantRepository.save(variant);

        InventoryMovement movement = new InventoryMovement();
        movement.setVariant(variant);
        movement.setMovementType(type);
        movement.setQuantityChange(quantity);
        movement.setNotes(reason.trim());

        return movementRepository.save(movement);
    }

    @Override
    @Transactional
    public ProductVariant updateLowStockThreshold(Long variantId, Integer threshold) {
        if (threshold == null || threshold < 0 || threshold > 1_000_000) {
            throw new BadRequestException("Low-stock threshold must be between 0 and 1000000");
        }
        ProductVariant variant = variantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        variant.setLowStockThreshold(threshold);
        return variantRepository.save(variant);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryMovement> getMovements(Long variantId, Pageable pageable) {
        return movementRepository.findByVariantVariantIdOrderByCreatedAtDesc(variantId, pageable);
    }
}
