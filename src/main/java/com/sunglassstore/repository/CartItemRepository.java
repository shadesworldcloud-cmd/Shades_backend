package com.sunglassstore.repository;

import com.sunglassstore.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartCartIdAndVariantVariantId(Long cartId, Long variantId);

    /**
     * Clears every customer's cart of these variants, so a product being deleted cannot leave a
     * line pointing at a variant that no longer exists. FK_CART_ITEM_VARIANT is NO ACTION, so
     * without this the product delete is rejected outright.
     *
     * A bulk delete rather than loading the entities: the number of carts holding a popular product
     * is unbounded, and none of the rows are needed in memory.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM CartItem c WHERE c.variant.variantId IN :variantIds")
    int deleteByVariantIds(@org.springframework.data.repository.query.Param("variantIds") java.util.Collection<Long> variantIds);
}
