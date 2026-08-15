package com.sunglassstore.repository;

import com.sunglassstore.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    long countByEmailIgnoreCaseAndIsSuccessfulFalseAndAttemptedAtAfter(String email,
                                                                        java.time.LocalDateTime attemptedAt);
}
