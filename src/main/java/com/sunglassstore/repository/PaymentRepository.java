package com.sunglassstore.repository;

import com.sunglassstore.entity.Payment;
import com.sunglassstore.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId")
    Optional<Payment> findByIdForUpdate(Long paymentId);

    Page<Payment> findByOrderOrderId(Long orderId, Pageable pageable);

    Optional<Payment> findFirstByOrderOrderIdAndPaymentStatus(Long orderId, PaymentStatus paymentStatus);
    boolean existsByOrderOrderIdAndPaymentStatusIn(Long orderId, List<PaymentStatus> statuses);
    List<Payment> findByOrderOrderIdOrderByCreatedAtDesc(Long orderId);
}
