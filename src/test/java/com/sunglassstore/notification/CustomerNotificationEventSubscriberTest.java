package com.sunglassstore.notification;

import com.sunglassstore.email.event.ReturnStatusEmailRequested;
import com.sunglassstore.email.event.ShipmentStatusEmailRequested;
import com.sunglassstore.email.event.OrderCancelledEmailRequested;
import com.sunglassstore.entity.Notification;
import com.sunglassstore.entity.User;
import com.sunglassstore.repository.NotificationRepository;
import com.sunglassstore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerNotificationEventSubscriberTest {
    private NotificationRepository notifications;
    private UserRepository users;
    private CustomerNotificationEventSubscriber subscriber;
    private com.sunglassstore.service.CommunicationPreferenceService preferences;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class); users = mock(UserRepository.class);
        preferences = mock(com.sunglassstore.service.CommunicationPreferenceService.class);
        when(preferences.allowsInApp(anyLong(), any())).thenReturn(true);
        subscriber = new CustomerNotificationEventSubscriber(notifications, users, preferences);
    }

    @Test
    void shipmentUpdateCreatesCustomerNotification() {
        User customer = user(7L); when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(customer));
        subscriber.onShipment(new ShipmentStatusEmailRequested("customer@example.com", "Customer", 22L, 3L,
                "IN_TRANSIT", "Courier", "TRACK-1", LocalDateTime.now().plusDays(2)));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertSame(customer, saved.getValue().getUser());
        assertTrue(saved.getValue().getMessage().contains("TRACK-1"));
        assertNotNull(saved.getValue().getSentAt());
    }

    @Test
    void newReturnNotifiesCustomerAndEveryActiveStaffUser() {
        User customer = user(7L); User admin = user(1L); User support = user(2L);
        when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(customer));
        when(users.findActiveUsersByRoleNames(List.of("ADMIN", "SUPPORT"))).thenReturn(List.of(admin, support));

        subscriber.onReturn(new ReturnStatusEmailRequested("customer@example.com", "Customer", 9L, 22L, "REQUESTED", null));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(3)).save(saved.capture());
        assertEquals(List.of(customer, admin, support), saved.getAllValues().stream().map(Notification::getUser).toList());
    }

    @Test
    void cancellationNotifiesCustomerAndStaff() {
        User customer = user(7L); User admin = user(1L);
        when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(customer));
        when(users.findActiveUsersByRoleNames(List.of("ADMIN", "SUPPORT"))).thenReturn(List.of(admin));
        subscriber.onOrderCancelled(new OrderCancelledEmailRequested(
                "customer@example.com", "Customer", 22L, new BigDecimal("312.20")));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(saved.capture());
        assertTrue(saved.getAllValues().getFirst().getMessage().contains("312.20"));
        assertSame(admin, saved.getAllValues().get(1).getUser());
    }

    @Test
    void customerOptOutDoesNotSuppressOperationalStaffAlert() {
        User customer = user(7L); User admin = user(1L);
        when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(customer));
        when(users.findActiveUsersByRoleNames(List.of("ADMIN", "SUPPORT"))).thenReturn(List.of(admin));
        when(preferences.allowsInApp(7L, com.sunglassstore.service.CommunicationPreferenceService.Topic.RETURN_REFUND)).thenReturn(false);
        subscriber.onReturn(new ReturnStatusEmailRequested("customer@example.com", "Customer", 9L, 22L, "REQUESTED", null));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertSame(admin, saved.getValue().getUser());
    }

    private User user(Long id) { User user = new User(); user.setUserId(id); return user; }
}
