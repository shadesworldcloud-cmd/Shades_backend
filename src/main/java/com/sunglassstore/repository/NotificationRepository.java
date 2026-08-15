package com.sunglassstore.repository;

import com.sunglassstore.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import com.sunglassstore.entity.enums.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserUserIdAndNotificationTypeOrderByCreatedAtDesc(Long userId, NotificationType type, Pageable pageable);
    long countByUserUserIdAndNotificationTypeAndReadAtIsNull(Long userId, NotificationType type);
    List<Notification> findByUserUserIdAndNotificationTypeAndReadAtIsNull(Long userId, NotificationType type);
    java.util.Optional<Notification> findByNotificationIdAndUserUserIdAndNotificationType(Long notificationId, Long userId, NotificationType type);
}
