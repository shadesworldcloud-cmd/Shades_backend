package com.sunglassstore.service.impl;

import com.sunglassstore.entity.Notification;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.NotificationType;
import com.sunglassstore.entity.enums.NotificationStatus;
import com.sunglassstore.dto.response.NotificationResponse;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.NotificationRepository;
import com.sunglassstore.service.NotificationService;
import com.sunglassstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    @Override
    @Transactional
    public Notification createNotification(Long userId, NotificationType type,
                                           String subject, String message) {
        User user = userService.findById(userId);
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setNotificationType(type);
        notification.setSubject(subject);
        notification.setMessage(message);
        if (type == NotificationType.IN_APP) {
            notification.setNotificationStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        }
        return notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserUserIdAndNotificationTypeOrderByCreatedAtDesc(userId, NotificationType.IN_APP, pageable)
                .map(NotificationResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserUserIdAndNotificationTypeAndReadAtIsNull(userId, NotificationType.IN_APP);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByNotificationIdAndUserUserIdAndNotificationType(notificationId, userId, NotificationType.IN_APP)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        markRead(notification);
        return NotificationResponse.fromEntity(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public long markAllRead(Long userId) {
        var unread = notificationRepository.findByUserUserIdAndNotificationTypeAndReadAtIsNull(userId, NotificationType.IN_APP);
        unread.forEach(this::markRead);
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    private void markRead(Notification notification) {
        if (notification.getReadAt() == null) notification.setReadAt(LocalDateTime.now());
        notification.setNotificationStatus(NotificationStatus.READ);
    }
}
