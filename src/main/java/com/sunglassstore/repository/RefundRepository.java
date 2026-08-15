package com.sunglassstore.repository;

import com.sunglassstore.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Refund r " +
           "WHERE r.payment.order.orderId = :orderId " +
           "AND r.returnRequest IS NULL " +
           "AND r.refundStatus IN (com.sunglassstore.entity.enums.RefundStatus.PENDING, " +
           "com.sunglassstore.entity.enums.RefundStatus.PROCESSING, " +
           "com.sunglassstore.entity.enums.RefundStatus.COMPLETED)")
    boolean hasActiveStandaloneRefundForOrder(Long orderId);

    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM Refund r " +
           "WHERE r.payment.paymentId = :paymentId " +
           "AND r.refundStatus IN (com.sunglassstore.entity.enums.RefundStatus.PENDING, " +
           "com.sunglassstore.entity.enums.RefundStatus.PROCESSING, " +
           "com.sunglassstore.entity.enums.RefundStatus.COMPLETED)")
    BigDecimal sumRefundedByPaymentId(Long paymentId);

    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM Refund r " +
           "WHERE r.returnRequest.returnId = :returnId " +
           "AND r.refundStatus IN (com.sunglassstore.entity.enums.RefundStatus.PENDING, " +
           "com.sunglassstore.entity.enums.RefundStatus.PROCESSING, " +
           "com.sunglassstore.entity.enums.RefundStatus.COMPLETED)")
    BigDecimal sumRefundedByReturnId(Long returnId);

    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM Refund r " +
           "WHERE r.payment.order.orderId = :orderId " +
           "AND r.refundStatus IN (com.sunglassstore.entity.enums.RefundStatus.PENDING, " +
           "com.sunglassstore.entity.enums.RefundStatus.PROCESSING, " +
           "com.sunglassstore.entity.enums.RefundStatus.COMPLETED)")
    BigDecimal sumRefundedByOrderId(Long orderId);

    List<Refund> findByReturnRequestReturnIdOrderByCreatedAtDesc(Long returnId);
}
