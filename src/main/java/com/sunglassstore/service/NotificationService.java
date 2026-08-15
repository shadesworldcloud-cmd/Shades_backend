package com.sunglassstore.service;

import com.sunglassstore.entity.Notification;
import com.sunglassstore.entity.enums.NotificationType;
import com.sunglassstore.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    Notification createNotification(Long userId, NotificationType type, String subject, String message);

    Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable);
    long getUnreadCount(Long userId);
    NotificationResponse markRead(Long userId, Long notificationId);
    long markAllRead(Long userId);
}
