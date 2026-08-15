package com.sunglassstore.entity;

import com.sunglassstore.entity.enums.NotificationStatus;
import com.sunglassstore.entity.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "NOTIFICATIONS")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTIFICATION_ID")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "NOTIFICATION_TYPE", nullable = false, length = 30)
    private NotificationType notificationType;

    @Column(name = "SUBJECT")
    private String subject;

    @Column(name = "MESSAGE", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "NOTIFICATION_STATUS", nullable = false, length = 20)
    private NotificationStatus notificationStatus = NotificationStatus.PENDING;

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt;

    @Column(name = "READ_AT")
    private LocalDateTime readAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
