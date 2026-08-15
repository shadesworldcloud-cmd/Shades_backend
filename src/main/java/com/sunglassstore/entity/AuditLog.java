package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "AUDIT_LOGS")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_LOG_ID")
    private Long auditLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID")
    private User user;

    @Column(name = "ACTION_TYPE", nullable = false, length = 100)
    private String actionType;

    @Column(name = "ENTITY_TYPE", nullable = false, length = 100)
    private String entityType;

    @Column(name = "ENTITY_ID")
    private Long entityId;

    @Column(name = "OLD_VALUE", columnDefinition = "JSON")
    private String oldValue;

    @Column(name = "NEW_VALUE", columnDefinition = "JSON")
    private String newValue;

    @Column(name = "IP_ADDRESS", length = 45)
    private String ipAddress;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
