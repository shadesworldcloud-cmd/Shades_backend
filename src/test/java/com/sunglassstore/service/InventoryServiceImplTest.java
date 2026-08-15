package com.sunglassstore.service;

import com.sunglassstore.entity.InventoryMovement;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.enums.MovementType;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.InventoryMovementRepository;
import com.sunglassstore.repository.ProductVariantRepository;
import com.sunglassstore.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryServiceImplTest {
    private ProductVariantRepository variants;
    private InventoryMovementRepository movements;
    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        variants = mock(ProductVariantRepository.class);
        movements = mock(InventoryMovementRepository.class);
        service = new InventoryServiceImpl(variants, movements);
    }

    @Test
    void purchaseAddsStockAndCreatesAuditableMovement() {
        ProductVariant variant = variant(5);
        when(variants.findByIdForUpdate(2L)).thenReturn(Optional.of(variant));
        when(movements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovement result = service.adjustInventory(2L, 8, MovementType.PURCHASE, "  Supplier receipt  ");

        assertEquals(13, variant.getQuantityAvailable());
        assertEquals("Supplier receipt", result.getNotes());
        assertEquals(MovementType.PURCHASE, result.getMovementType());
    }

    @Test
    void manualSaleMovementIsRejected() {
        assertThrows(BadRequestException.class,
                () -> service.adjustInventory(2L, -1, MovementType.SALE, "Manual sale"));
        verifyNoInteractions(variants, movements);
    }

    @Test
    void zeroAndNegativeResultAreRejected() {
        assertThrows(BadRequestException.class,
                () -> service.adjustInventory(2L, 0, MovementType.ADJUSTMENT, "No change"));
        ProductVariant variant = variant(3);
        when(variants.findByIdForUpdate(2L)).thenReturn(Optional.of(variant));
        assertThrows(BadRequestException.class,
                () -> service.adjustInventory(2L, -4, MovementType.ADJUSTMENT, "Correction"));
        verify(movements, never()).save(any());
    }

    @Test
    void updatesLowStockThresholdUnderLock() {
        ProductVariant variant = variant(10);
        when(variants.findByIdForUpdate(2L)).thenReturn(Optional.of(variant));
        when(variants.save(variant)).thenReturn(variant);

        ProductVariant updated = service.updateLowStockThreshold(2L, 12);

        assertEquals(12, updated.getLowStockThreshold());
    }

    private ProductVariant variant(int quantity) {
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(2L);
        variant.setQuantityAvailable(quantity);
        variant.setLowStockThreshold(5);
        return variant;
    }
}
