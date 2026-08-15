package com.sunglassstore.entity;

import com.sunglassstore.entity.enums.EmailOutboxStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "EMAIL_OUTBOX", indexes = {
        @Index(name = "IDX_EMAIL_OUTBOX_DUE", columnList = "STATUS,NEXT_ATTEMPT_AT,CREATED_AT")
})
@Getter
@Setter
@NoArgsConstructor
public class EmailOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EMAIL_OUTBOX_ID")
    private Long emailOutboxId;

    @Column(name = "RECIPIENT", nullable = false)
    private String recipient;

    @Column(name = "SUBJECT", nullable = false)
    private String subject;

    @Column(name = "BODY", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private int attemptCount;

    @Column(name = "NEXT_ATTEMPT_AT", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "LAST_ERROR", length = 1000)
    private String lastError;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt;

    @Column(name = "EXPIRES_AT")
    private LocalDateTime expiresAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }
}
