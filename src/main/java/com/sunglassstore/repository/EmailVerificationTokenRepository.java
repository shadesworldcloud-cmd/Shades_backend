package com.sunglassstore.repository;

import com.sunglassstore.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EmailVerificationToken t JOIN FETCH t.user WHERE t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<EmailVerificationToken> findTopByUserUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.verifiedAt = CURRENT_TIMESTAMP " +
            "WHERE t.user.userId = :userId AND t.verifiedAt IS NULL")
    void invalidateAllForUser(@Param("userId") Long userId);
}
