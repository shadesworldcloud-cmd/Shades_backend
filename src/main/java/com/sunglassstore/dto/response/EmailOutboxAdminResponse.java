package com.sunglassstore.dto.response;

import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;

import java.time.LocalDateTime;

public record EmailOutboxAdminResponse(
        Long emailOutboxId,
        String recipient,
        String subject,
        EmailOutboxStatus status,
        int attemptCount,
        LocalDateTime nextAttemptAt,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime sentAt,
        LocalDateTime expiresAt,
        boolean canRetry
) {
    public static EmailOutboxAdminResponse fromEntity(EmailOutbox email, LocalDateTime now) {
        boolean unexpired = email.getExpiresAt() == null || email.getExpiresAt().isAfter(now);
        boolean retryable = email.getStatus() == EmailOutboxStatus.FAILED
                && email.getBody() != null && !email.getBody().isBlank() && unexpired;
        return new EmailOutboxAdminResponse(
                email.getEmailOutboxId(), email.getRecipient(), email.getSubject(), email.getStatus(),
                email.getAttemptCount(), email.getNextAttemptAt(), email.getLastError(), email.getCreatedAt(),
                email.getSentAt(), email.getExpiresAt(), retryable);
    }
}
