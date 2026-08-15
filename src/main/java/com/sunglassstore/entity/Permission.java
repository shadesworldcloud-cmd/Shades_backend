package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PERMISSIONS")
@Getter
@Setter
@NoArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PERMISSION_ID")
    private Long permissionId;

    @Column(name = "PERMISSION_NAME", nullable = false, unique = true, length = 100)
    private String permissionName;

    @Column(name = "DESCRIPTION")
    private String description;
}
