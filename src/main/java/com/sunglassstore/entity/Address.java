package com.sunglassstore.entity;

import com.sunglassstore.entity.enums.AddressType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ADDRESSES")
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ADDRESS_ID")
    private Long addressId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "ADDRESS_TYPE", length = 20)
    private AddressType addressType = AddressType.SHIPPING;

    @Column(name = "RECIPIENT_NAME", nullable = false)
    private String recipientName;
    /**
     * Hibernate-managed optimistic lock. Safe to automate here because an address is only ever
     * written through the customer read-edit-save path, so a version bump always corresponds to a
     * real user edit — unlike User, which is also written by background auth bookkeeping.
     */
    @jakarta.persistence.Version
    @Column(name = "VERSION", nullable = false)
    private Long version = 0L;


    @Column(name = "PHONE_NUMBER", length = 20)
    private String phoneNumber;

    @Column(name = "HOUSE_NUMBER", length = 50)
    private String houseNumber;

    @Column(name = "ADDRESS_LINE_1", nullable = false)
    private String addressLine1;

    @Column(name = "ADDRESS_LINE_2")
    private String addressLine2;

    @Column(name = "CITY", nullable = false, length = 100)
    private String city;

    @Column(name = "STATE", nullable = false, length = 100)
    private String state;

    @Column(name = "PINCODE", nullable = false, length = 20)
    private String pincode;

    @Column(name = "COUNTRY", nullable = false, length = 100)
    private String country;

    @Column(name = "IS_DEFAULT", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
