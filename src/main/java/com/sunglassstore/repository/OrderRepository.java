package com.sunglassstore.repository;

import com.sunglassstore.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId")
    Optional<Order> findByIdForUpdate(Long orderId);

    Page<Order> findByUserUserIdOrderByPurchasedAtDesc(Long userId, Pageable pageable);

    /**
     * Idempotency lookup. Scoped to the user as well as the key so one customer's key can never
     * return another customer's order, however the key was generated.
     */
    Optional<Order> findByIdempotencyKeyAndUserUserId(String idempotencyKey, Long userId);

    Optional<Order> findByOrderIdAndUserUserId(Long orderId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId AND o.user.userId = :userId")
    Optional<Order> findByOrderIdAndUserUserIdForUpdate(Long orderId, Long userId);

    Page<Order> findAllByOrderByPurchasedAtDesc(Pageable pageable);

    long countByUserUserId(Long userId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.user.userId = :userId AND o.orderStatus <> com.sunglassstore.entity.enums.OrderStatus.CANCELLED")
    BigDecimal sumCompletedValueByUserId(Long userId);

    @Query("SELECT MAX(o.purchasedAt) FROM Order o WHERE o.user.userId = :userId")
    LocalDateTime findLastOrderAtByUserId(Long userId);

    /**
     * Orders still awaiting payment past the reservation window. Stock is deducted when the order
     * is created, so an order that is never paid holds that stock away from other customers
     * indefinitely; these are the candidates to expire and restore.
     *
     * Deliberately excludes any order that has a PAID or PARTIALLY_REFUNDED payment, so a
     * successful payment recorded moments before the sweep can never have its order expired.
     * Ids only — each is then re-read under a row lock before being touched.
     */
    @Query("SELECT o.orderId FROM Order o WHERE o.orderStatus = com.sunglassstore.entity.enums.OrderStatus.PLACED "
            + "AND o.purchasedAt < :cutoff AND NOT EXISTS ("
            + "  SELECT 1 FROM Payment p WHERE p.order = o AND p.paymentStatus IN ("
            + "    com.sunglassstore.entity.enums.PaymentStatus.PAID,"
            + "    com.sunglassstore.entity.enums.PaymentStatus.PARTIALLY_REFUNDED) ) "
            + "ORDER BY o.orderId")
    List<Long> findUnpaidOrderIdsPlacedBefore(LocalDateTime cutoff);
}
