package com.sunglassstore.email;

import com.sunglassstore.email.event.PasswordResetEmailRequested;
import com.sunglassstore.email.event.EmailVerificationRequested;
import com.sunglassstore.email.event.OrderCancelledEmailRequested;
import com.sunglassstore.email.event.RefundCompletedEmailRequested;
import com.sunglassstore.email.event.ReturnStatusEmailRequested;
import com.sunglassstore.email.event.ShipmentStatusEmailRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CustomerEmailEventSubscriberTest {
    private EmailOutboxService outboxService;
    private com.sunglassstore.service.CommunicationPreferenceService preferences;
    private CustomerEmailEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        outboxService = mock(EmailOutboxService.class);
        preferences = mock(com.sunglassstore.service.CommunicationPreferenceService.class);
        when(preferences.allowsEmail(anyString(), any())).thenReturn(true);
        subscriber = new CustomerEmailEventSubscriber(outboxService, preferences);
        ReflectionTestUtils.setField(subscriber, "frontendBaseUrl", "http://localhost:3000");
    }

    @Test
    void createsPasswordResetEmailWithFrontendLink() {
        subscriber.onPasswordReset(new PasswordResetEmailRequested("customer@example.com", "Asha", "raw-token"));
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxService).enqueue(captor.capture(), expiry.capture());
        EmailMessage message = captor.getValue();
        assertTrue(message.subject().contains("Reset"));
        assertTrue(message.body().contains("http://localhost:3000/signin?resetToken=raw-token"));
        assertTrue(expiry.getValue().isAfter(LocalDateTime.now().plusMinutes(29)));
    }

    @Test
    void createsExpiringEmailVerificationMessage() {
        subscriber.onEmailVerification(new EmailVerificationRequested("customer@example.com", "Asha", "verify-token"));
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxService).enqueue(captor.capture(), expiry.capture());
        assertTrue(captor.getValue().subject().contains("Verify"));
        assertTrue(captor.getValue().body().contains("/signin?verifyToken=verify-token"));
        assertTrue(expiry.getValue().isAfter(LocalDateTime.now().plusHours(23)));
    }

    @Test
    void createsSingleCancellationMessageWithRefundAmount() {
        subscriber.onOrderCancelled(new OrderCancelledEmailRequested(
                "customer@example.com", "Asha", 22L, new BigDecimal("312.20")));
        EmailMessage message = capture();
        assertTrue(message.subject().contains("Order #22"));
        assertTrue(message.body().contains("INR 312.20"));
        assertTrue(message.body().contains("released back into store inventory"));
    }

    @Test
    void createsReturnStatusEmail() {
        subscriber.onReturnStatus(new ReturnStatusEmailRequested(
                "customer@example.com", "Asha", 4L, 7L, "APPROVED", "Pickup tomorrow"));
        EmailMessage message = capture();
        assertTrue(message.subject().contains("Return #4"));
        assertTrue(message.body().contains("Pickup tomorrow"));
    }

    @Test
    void createsRefundCompletionEmail() {
        subscriber.onRefundCompleted(new RefundCompletedEmailRequested(
                "customer@example.com", "Asha", 8L, 7L, new BigDecimal("3500.00")));
        EmailMessage message = capture();
        assertTrue(message.subject().contains("order #7"));
        assertTrue(message.body().contains("INR 3500.00"));
    }

    @Test
    void createsShipmentTrackingEmail() {
        subscriber.onShipmentStatus(new ShipmentStatusEmailRequested(
                "customer@example.com", "Asha", 7L, 3L, "OUT_FOR_DELIVERY",
                "BlueDart", "BD-123", LocalDateTime.now().plusDays(1)));
        EmailMessage message = capture();
        assertTrue(message.subject().contains("Order #7"));
        assertTrue(message.body().contains("BlueDart"));
        assertTrue(message.body().contains("BD-123"));
        assertTrue(message.body().contains("out for delivery"));
    }

    @Test
    void optedOutShipmentEmailIsNotQueuedButSecurityEmailStillIs() {
        when(preferences.allowsEmail("customer@example.com", com.sunglassstore.service.CommunicationPreferenceService.Topic.SHIPMENT)).thenReturn(false);
        subscriber.onShipmentStatus(new ShipmentStatusEmailRequested("customer@example.com", "Asha", 7L, 3L,
                "SHIPPED", "Courier", "TRACK", null));
        verifyNoInteractions(outboxService);
        subscriber.onPasswordReset(new PasswordResetEmailRequested("customer@example.com", "Asha", "token"));
        verify(outboxService).enqueue(any(EmailMessage.class), any(LocalDateTime.class));
    }

    private EmailMessage capture() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(outboxService).enqueue(captor.capture());
        return captor.getValue();
    }
}
