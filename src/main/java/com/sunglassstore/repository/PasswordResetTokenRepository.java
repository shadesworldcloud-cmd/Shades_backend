package com.sunglassstore.repository;

import com.sunglassstore.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

    Optional<PasswordResetToken> findTopByUserUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = CURRENT_TIMESTAMP " +
           "WHERE t.user.userId = :userId AND t.usedAt IS NULL")
    void invalidateAllForUser(Long userId);
}
