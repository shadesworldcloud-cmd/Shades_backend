package com.sunglassstore.service;

import com.sunglassstore.entity.Notification;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.NotificationStatus;
import com.sunglassstore.entity.enums.NotificationType;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.NotificationRepository;
import com.sunglassstore.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {
    private NotificationRepository repository;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationServiceImpl(repository, mock(UserService.class));
    }

    @Test
    void inAppNotificationIsImmediatelySent() {
        UserService users = mock(UserService.class); User user = new User(); user.setUserId(7L);
        service = new NotificationServiceImpl(repository, users);
        when(users.findById(7L)).thenReturn(user);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        Notification result = service.createNotification(7L, NotificationType.IN_APP, "Order update", "Ready");

        assertEquals(NotificationStatus.SENT, result.getNotificationStatus());
        assertNotNull(result.getSentAt());
    }

    @Test
    void userCannotReadAnotherUsersNotification() {
        when(repository.findByNotificationIdAndUserUserIdAndNotificationType(12L, 7L, NotificationType.IN_APP)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.markRead(7L, 12L));
        verify(repository, never()).save(any());
    }

    @Test
    void markAllOnlyChangesAuthenticatedUsersUnreadNotifications() {
        Notification first = notification(); Notification second = notification();
        when(repository.findByUserUserIdAndNotificationTypeAndReadAtIsNull(7L, NotificationType.IN_APP)).thenReturn(List.of(first, second));

        assertEquals(2, service.markAllRead(7L));
        assertNotNull(first.getReadAt()); assertNotNull(second.getReadAt());
        assertEquals(NotificationStatus.READ, first.getNotificationStatus());
        verify(repository).saveAll(List.of(first, second));
    }

    private Notification notification() {
        Notification notification = new Notification(); notification.setNotificationStatus(NotificationStatus.SENT);
        notification.setCreatedAt(LocalDateTime.now()); return notification;
    }
}
