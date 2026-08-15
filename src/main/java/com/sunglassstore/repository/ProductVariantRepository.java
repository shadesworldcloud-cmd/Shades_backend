package com.sunglassstore.repository;

import com.sunglassstore.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    /**
     * Acquires a pessimistic write lock on the variant row.
     * Used during order creation to prevent concurrent inventory over-deduction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.variantId = :variantId")
    Optional<ProductVariant> findByIdForUpdate(Long variantId);
}
