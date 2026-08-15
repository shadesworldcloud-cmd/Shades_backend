package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "CONFIG")
@Getter
@Setter
@NoArgsConstructor
public class AppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONFIG_ID")
    private Long configId;

    @Column(name = "CONFIG_SHORT_CODE", nullable = false, unique = true, length = 100)
    private String configShortCode;

    @Column(name = "CONFIG_VALUE", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
    }
}
