package com.sunglassstore.email;

import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import com.sunglassstore.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The two SHORT database transactions either side of an SMTP send.
 *
 * This is a separate bean on purpose. The orchestration in EmailOutboxDeliveryService must not be
 * transactional — that is the entire point — and a self-invoked {@code this.claim()} would bypass
 * Spring's proxy and silently run with no transaction at all. Crossing a bean boundary is what
 * makes @Transactional actually apply here.
 */
@Service
@RequiredArgsConstructor
public class EmailOutboxTransactions {

    private final EmailOutboxRepository repository;

    /**
     * How long a claimed message is invisible to other workers. Must comfortably exceed the SMTP
     * timeout, or a slow send could be claimed a second time and the customer gets two emails.
     */
    @Value("${app.email.outbox.lease-minutes:5}")
    private int leaseMinutes;

    @Value("${app.email.outbox.max-attempts:8}")
    private int maxAttempts;

    /** What the sender needs, detached from the persistence context before the transaction ends. */
    public record Claimed(Long id, String recipient, String subject, String body) {
    }

    /**
     * progressed = "this call did work, keep draining"; message = null when the work was settled
     * entirely in the database (an expired message) and there is nothing to send.
     *
     * The distinction matters to the scheduler, which drains with {@code while (deliverNext())}.
     * Collapsing "expired, handled" into "nothing due" would stop the drain at the first expired
     * message and leave the rest of the queue for the next tick.
     */
    public record Claim(boolean progressed, Claimed message) {
    }

    /**
     * Takes the next due message and leases it, in one short transaction.
     *
     * The row lock (FOR UPDATE SKIP LOCKED) is released when this commits — before any network I/O
     * happens. Pushing NEXT_ATTEMPT_AT beyond the lease window is what stops another worker picking
     * the same row up in the meantime, so the claim survives the lock being released.
     *
     * @return the message to send, or null when nothing is due.
     */
    @Transactional
    public Claim claimNext() {
        EmailOutbox email = repository.findNextDueForUpdate(LocalDateTime.now()).orElse(null);
        if (email == null) {
            return new Claim(false, null);
        }

        // Expiry is a database-only decision, so it is settled here rather than costing a send.
        if (email.getExpiresAt() != null && !email.getExpiresAt().isAfter(LocalDateTime.now())) {
            email.setStatus(EmailOutboxStatus.FAILED);
            email.setLastError("Email expired before delivery");
            email.setBody("");
            repository.save(email);
            return new Claim(true, null);
        }

        email.setNextAttemptAt(LocalDateTime.now().plusMinutes(leaseMinutes));
        repository.save(email);
        return new Claim(true, new Claimed(email.getEmailOutboxId(), email.getRecipient(),
                email.getSubject(), email.getBody()));
    }

    /** Records a delivered message. REQUIRES_NEW so it can never join a caller's transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSent(Long id) {
        repository.findByIdForUpdate(id).ifPresent(email -> {
            email.setStatus(EmailOutboxStatus.SENT);
            email.setSentAt(LocalDateTime.now());
            email.setLastError(null);
            email.setBody("");
            repository.save(email);
        });
    }

    /** Records a failed attempt, scheduling a retry or giving up. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long id, String error) {
        repository.findByIdForUpdate(id).ifPresent(email -> {
            int attempts = email.getAttemptCount() + 1;
            email.setAttemptCount(attempts);
            email.setLastError(error);
            if (attempts >= maxAttempts) {
                email.setStatus(EmailOutboxStatus.FAILED);
                // Expiring messages carry time-sensitive secrets (reset links); normal
                // notifications keep their payload so an operator can safely re-queue them.
                if (email.getExpiresAt() != null) email.setBody("");
            } else {
                email.setStatus(EmailOutboxStatus.RETRY);
                long delayMinutes = Math.min(1L << Math.min(attempts - 1, 10), 24L * 60L);
                email.setNextAttemptAt(LocalDateTime.now().plusMinutes(delayMinutes));
            }
            repository.save(email);
        });
    }
}
