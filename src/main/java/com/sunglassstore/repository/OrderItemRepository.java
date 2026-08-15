package com.sunglassstore.repository;

import com.sunglassstore.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT oi FROM OrderItem oi WHERE oi.orderItemId = :orderItemId")
    Optional<OrderItem> findByIdForUpdate(Long orderItemId);

    /** True when any order line references the variant — the guard that turns delete into archive. */
    boolean existsByVariantVariantId(Long variantId);

    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END " +
           "FROM OrderItem oi WHERE oi.order.user.userId = :userId " +
           "AND oi.variant.product.productId = :productId " +
           "AND oi.order.orderStatus = com.sunglassstore.entity.enums.OrderStatus.DELIVERED")
    boolean hasUserPurchasedProduct(Long userId, Long productId);

    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.user.userId = :userId " +
           "AND oi.variant.product.productId = :productId " +
           "AND oi.order.orderStatus = com.sunglassstore.entity.enums.OrderStatus.DELIVERED " +
           "ORDER BY oi.order.deliveredAt DESC")
    List<OrderItem> findDeliveredByUserAndProduct(Long userId, Long productId);
}
