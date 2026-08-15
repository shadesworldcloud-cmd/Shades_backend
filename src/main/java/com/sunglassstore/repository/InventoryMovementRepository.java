package com.sunglassstore.repository;

import com.sunglassstore.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @EntityGraph(attributePaths = "variant")
    Page<InventoryMovement> findByVariantVariantIdOrderByCreatedAtDesc(Long variantId, Pageable pageable);

    /**
     * Drops the stock ledger for these variants as part of deleting their product.
     *
     * This does discard an audit trail, and that is the deliberate reading of "remove it from
     * everywhere in the inventory": a movement history for a product that no longer exists cannot
     * be reconciled against anything. The financial record is unaffected — that lives in
     * ORDER_ITEMS, which survives the deletion with its own price and quantity snapshot.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM InventoryMovement m WHERE m.variant.variantId IN :variantIds")
    int deleteByVariantIds(@org.springframework.data.repository.query.Param("variantIds") java.util.Collection<Long> variantIds);
}
