package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "LOGIN_ATTEMPTS")
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOGIN_ATTEMPT_ID")
    private Long loginAttemptId;

    @Column(name = "EMAIL", nullable = false)
    private String email;

    @Column(name = "IP_ADDRESS", length = 45)
    private String ipAddress;

    @Column(name = "IS_SUCCESSFUL", nullable = false)
    private Boolean isSuccessful;

    @Column(name = "FAILURE_REASON")
    private String failureReason;

    @Column(name = "ATTEMPTED_AT", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;

    @PrePersist
    protected void onCreate() {
        attemptedAt = LocalDateTime.now();
    }
}
