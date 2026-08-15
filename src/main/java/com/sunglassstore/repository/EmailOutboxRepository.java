package com.sunglassstore.repository;

import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long>, JpaSpecificationExecutor<EmailOutbox> {
    @Query(value = """
            SELECT * FROM EMAIL_OUTBOX
            WHERE STATUS IN ('PENDING', 'RETRY') AND NEXT_ATTEMPT_AT <= :now
            ORDER BY CREATED_AT ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<EmailOutbox> findNextDueForUpdate(@Param("now") LocalDateTime now);

    long countByStatus(EmailOutboxStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select email from EmailOutbox email where email.emailOutboxId = :id")
    Optional<EmailOutbox> findByIdForUpdate(@Param("id") Long id);
}
