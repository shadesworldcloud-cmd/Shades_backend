package com.sunglassstore.email;

import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import com.sunglassstore.repository.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The outbox state machine, which moved here when delivery was split so the SMTP call could happen
 * outside a transaction. These are the same behaviours EmailOutboxDeliveryServiceTest used to
 * assert — retry backoff, giving up, expiry, payload scrubbing — now tested where they live.
 */
class EmailOutboxTransactionsTest {
    private EmailOutboxRepository repository;
    private EmailOutboxTransactions transactions;

    @BeforeEach
    void setUp() {
        repository = mock(EmailOutboxRepository.class);
        transactions = new EmailOutboxTransactions(repository);
        ReflectionTestUtils.setField(transactions, "maxAttempts", 3);
        ReflectionTestUtils.setField(transactions, "leaseMinutes", 5);
    }

    @Test
    void nothingDueReportsNoProgress() {
        when(repository.findNextDueForUpdate(any())).thenReturn(Optional.empty());
        EmailOutboxTransactions.Claim claim = transactions.claimNext();
        assertFalse(claim.progressed());
        assertNull(claim.message());
    }

    @Test
    void claimingLeasesTheRowSoAnotherWorkerCannotTakeIt() {
        EmailOutbox email = queued(0);
        when(repository.findNextDueForUpdate(any())).thenReturn(Optional.of(email));

        EmailOutboxTransactions.Claim claim = transactions.claimNext();

        assertTrue(claim.progressed());
        assertEquals("customer@example.com", claim.message().recipient());
        // The lease is what protects the message once the row lock is released at commit.
        assertTrue(email.getNextAttemptAt().isAfter(LocalDateTime.now().plusMinutes(4)),
                "the claimed row must be pushed out of the due window");
        // Claiming is not a delivery attempt, so neither status nor attempt count moves yet.
        assertEquals(EmailOutboxStatus.PENDING, email.getStatus());
        assertEquals(0, email.getAttemptCount());
    }

    @Test
    void expiredSensitiveEmailIsNeverSentAndPayloadIsScrubbed() {
        EmailOutbox email = queued(0);
        email.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findNextDueForUpdate(any())).thenReturn(Optional.of(email));

        EmailOutboxTransactions.Claim claim = transactions.claimNext();

        // Progress was made, but there is nothing to send, so the scheduler must keep draining.
        assertTrue(claim.progressed());
        assertNull(claim.message());
        assertEquals(EmailOutboxStatus.FAILED, email.getStatus());
        assertEquals("", email.getBody());
        assertEquals("Email expired before delivery", email.getLastError());
    }

    @Test
    void successfulDeliveryMarksMessageSentAndScrubsPayload() {
        EmailOutbox email = queued(0);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(email));

        transactions.recordSent(7L);

        assertEquals(EmailOutboxStatus.SENT, email.getStatus());
        assertNotNull(email.getSentAt());
        assertEquals("", email.getBody());
        assertNull(email.getLastError());
        verify(repository).save(email);
    }

    @Test
    void temporaryFailureSchedulesRetryWithError() {
        EmailOutbox email = queued(0);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(email));

        transactions.recordFailure(7L, "SMTP down");

        assertEquals(EmailOutboxStatus.RETRY, email.getStatus());
        assertEquals(1, email.getAttemptCount());
        assertEquals("SMTP down", email.getLastError());
        assertTrue(email.getNextAttemptAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void finalFailureStopsAutomaticRetryAndKeepsANonSensitivePayload() {
        EmailOutbox email = queued(2);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(email));

        transactions.recordFailure(7L, "Rejected");

        assertEquals(EmailOutboxStatus.FAILED, email.getStatus());
        assertEquals(3, email.getAttemptCount());
        assertEquals("Body", email.getBody());
    }

    private EmailOutbox queued(int attempts) {
        EmailOutbox email = new EmailOutbox();
        email.setRecipient("customer@example.com");
        email.setSubject("Subject");
        email.setBody("Body");
        email.setAttemptCount(attempts);
        email.setStatus(attempts == 0 ? EmailOutboxStatus.PENDING : EmailOutboxStatus.RETRY);
        email.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        return email;
    }
}
