package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "USERS")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;

    @Column(name = "PASSWORD_HASH", nullable = false)
    private String passwordHash;

    @Column(name = "NAME", nullable = false)
    private String name;
    /**
     * Row version for optimistic locking on the profile edit path.
     *
     * Deliberately NOT annotated @Version. Hibernate would then version-check EVERY write to a
     * User — including background ones like lastLoginAt and failedLoginAttempts — and start
     * raising conflicts in flows that have no concurrent-edit problem. This column is read and
     * incremented by exactly one query: UserRepository.updateEditableProfile.
     */
    @Column(name = "VERSION", nullable = false)
    private Long version = 0L;


    @Column(name = "PHONE_NUMBER", length = 20)
    private String phoneNumber;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    @Column(name = "EMAIL_VERIFIED", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "ACCOUNT_LOCKED", nullable = false)
    private Boolean accountLocked = false;

    @Column(name = "FAILED_LOGIN_ATTEMPTS", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "LOCKED_UNTIL")
    private LocalDateTime lockedUntil;

    @Column(name = "PASSWORD_CHANGED_AT")
    private LocalDateTime passwordChangedAt;

    @Column(name = "LAST_LOGIN_AT")
    private LocalDateTime lastLoginAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "USER_ROLES",
            joinColumns = @JoinColumn(name = "USER_ID"),
            inverseJoinColumns = @JoinColumn(name = "ROLE_ID")
    )
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
