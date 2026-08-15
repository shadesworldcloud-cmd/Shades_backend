package com.sunglassstore.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Orchestration only: that the SMTP call is bracketed BETWEEN two short database transactions
 * rather than sitting inside one.
 *
 * The ordering assertion is the point of this file. It pins the property the split exists for —
 * claim commits, then the network call happens holding no lock and no connection, then the outcome
 * is recorded — so a future refactor that folds the send back inside a transaction fails here.
 */
class EmailOutboxDeliveryServiceTest {
    private EmailOutboxTransactions transactions;
    private EmailService emailService;
    private EmailOutboxDeliveryService delivery;

    @BeforeEach
    void setUp() {
        transactions = mock(EmailOutboxTransactions.class);
        emailService = mock(EmailService.class);
        delivery = new EmailOutboxDeliveryService(transactions, emailService);
    }

    private static EmailOutboxTransactions.Claim claimed() {
        return new EmailOutboxTransactions.Claim(true,
                new EmailOutboxTransactions.Claimed(7L, "customer@example.com", "Subject", "Body"));
    }

    @Test
    void nothingDueMeansNoSendAndNoFurtherDraining() {
        when(transactions.claimNext()).thenReturn(new EmailOutboxTransactions.Claim(false, null));
        assertFalse(delivery.deliverNext());
        verifyNoInteractions(emailService);
    }

    @Test
    void aMessageSettledInTheDatabaseStillCountsAsProgress() {
        // An expired message is handled entirely inside the claim transaction. The scheduler must
        // keep draining, so this reports true even though nothing was sent.
        when(transactions.claimNext()).thenReturn(new EmailOutboxTransactions.Claim(true, null));
        assertTrue(delivery.deliverNext());
        verifyNoInteractions(emailService);
    }

    @Test
    void theSendHappensBetweenTheTwoTransactions() {
        when(transactions.claimNext()).thenReturn(claimed());

        assertTrue(delivery.deliverNext());

        InOrder order = inOrder(transactions, emailService);
        order.verify(transactions).claimNext();
        order.verify(emailService).send(any());
        order.verify(transactions).recordSent(7L);
        order.verifyNoMoreInteractions();
    }

    @Test
    void aFailedSendIsRecordedRatherThanPropagated() {
        when(transactions.claimNext()).thenReturn(claimed());
        doThrow(new EmailDeliveryException("SMTP down", new RuntimeException()))
                .when(emailService).send(any());

        assertTrue(delivery.deliverNext(), "a failed attempt is still progress");

        verify(transactions).recordFailure(eq(7L), eq("SMTP down"));
        verify(transactions, never()).recordSent(any());
    }
}
