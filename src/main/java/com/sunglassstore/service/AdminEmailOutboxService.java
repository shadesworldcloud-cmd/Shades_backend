package com.sunglassstore.service;

import com.sunglassstore.dto.response.EmailOutboxAdminResponse;
import com.sunglassstore.dto.response.EmailOutboxSummaryResponse;
import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminEmailOutboxService {
    private final EmailOutboxRepository repository;

    @Transactional(readOnly = true)
    public Page<EmailOutboxAdminResponse> getMessages(EmailOutboxStatus status, String search, Pageable pageable) {
        Specification<EmailOutbox> specification = (root, query, builder) -> builder.conjunction();
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("recipient")), pattern),
                    builder.like(builder.lower(root.get("subject")), pattern)));
        }
        LocalDateTime now = LocalDateTime.now();
        return repository.findAll(specification, pageable)
                .map(email -> EmailOutboxAdminResponse.fromEntity(email, now));
    }

    @Transactional(readOnly = true)
    public EmailOutboxSummaryResponse getSummary() {
        long pending = repository.countByStatus(EmailOutboxStatus.PENDING);
        long retry = repository.countByStatus(EmailOutboxStatus.RETRY);
        long sent = repository.countByStatus(EmailOutboxStatus.SENT);
        long failed = repository.countByStatus(EmailOutboxStatus.FAILED);
        return new EmailOutboxSummaryResponse(pending + retry + sent + failed, pending, retry, sent, failed);
    }

    @Transactional
    public EmailOutboxAdminResponse retry(Long id) {
        EmailOutbox email = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Email outbox message not found"));
        if (email.getStatus() != EmailOutboxStatus.FAILED) {
            throw new BadRequestException("Only failed email messages can be retried");
        }
        LocalDateTime now = LocalDateTime.now();
        if (email.getExpiresAt() != null && !email.getExpiresAt().isAfter(now)) {
            throw new BadRequestException("This email has expired and cannot be retried");
        }
        if (email.getBody() == null || email.getBody().isBlank()) {
            throw new BadRequestException("This email's content was securely removed and cannot be retried");
        }
        email.setStatus(EmailOutboxStatus.RETRY);
        email.setAttemptCount(0);
        email.setNextAttemptAt(now);
        email.setLastError(null);
        email.setSentAt(null);
        return EmailOutboxAdminResponse.fromEntity(repository.save(email), now);
    }
}
