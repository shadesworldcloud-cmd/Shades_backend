package com.sunglassstore.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Delivers one queued email per call.
 *
 * Deliberately NOT @Transactional. It used to be, and that was a real reliability problem rather
 * than a stylistic one: the method held a {@code SELECT ... FOR UPDATE} row lock on the outbox row
 * AND a pooled database connection for the entire duration of an SMTP conversation with an external
 * mail server. A slow or hanging Gmail connection therefore pinned a connection out of a pool of
 * ten, and the row stayed locked behind it. Enough stuck sends and the pool is exhausted, which
 * takes down request handling that has nothing to do with email.
 *
 * The work is now three phases, and only the first and third touch the database:
 *
 *   1. claim  — short transaction: lease the row, commit, release the lock.
 *   2. send   — NO transaction, NO connection held. The network call happens here.
 *   3. record — short transaction: write the outcome.
 *
 * The delivery guarantee is unchanged and is still at-least-once: if the process dies between (1)
 * and (3) the lease expires and the message is retried, which is the normal and correct property of
 * an outbox. Duplicate suppression comes from the lease window being longer than the SMTP timeout.
 */
@Service
@RequiredArgsConstructor
public class EmailOutboxDeliveryService {

    private final EmailOutboxTransactions transactions;
    private final EmailService emailService;

    /** @return true when a message was claimed, so the scheduler knows to keep draining. */
    public boolean deliverNext() {
        EmailOutboxTransactions.Claim claim = transactions.claimNext();
        if (!claim.progressed()) {
            return false;
        }
        // Settled in the database (expired). Work was done, so the scheduler should keep draining.
        if (claim.message() == null) {
            return true;
        }
        EmailOutboxTransactions.Claimed claimed = claim.message();

        try {
            // Outside any transaction. This is the whole reason the method is split up.
            emailService.send(new EmailMessage(claimed.recipient(), claimed.subject(), claimed.body()));
            transactions.recordSent(claimed.id());
        } catch (RuntimeException exception) {
            transactions.recordFailure(claimed.id(), truncate(exception.getMessage()));
        }
        return true;
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) return "Email delivery failed";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
