package com.sunglassstore.repository;

import com.sunglassstore.entity.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReturnRequest r WHERE r.returnId = :returnId")
    Optional<ReturnRequest> findByIdForUpdate(Long returnId);

    Page<ReturnRequest> findByUserUserIdOrderByRequestedAtDesc(Long userId, Pageable pageable);

    Page<ReturnRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
}
