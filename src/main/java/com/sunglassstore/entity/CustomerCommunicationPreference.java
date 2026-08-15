package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "CUSTOMER_COMMUNICATION_PREFERENCES")
@Getter @Setter @NoArgsConstructor
public class CustomerCommunicationPreference {
    @Id
    @Column(name = "USER_ID")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "USER_ID")
    private User user;

    @Column(name = "EMAIL_ORDER_UPDATES", nullable = false)
    private Boolean emailOrderUpdates = true;
    @Column(name = "EMAIL_SHIPMENT_UPDATES", nullable = false)
    private Boolean emailShipmentUpdates = true;
    @Column(name = "EMAIL_RETURN_REFUND_UPDATES", nullable = false)
    private Boolean emailReturnRefundUpdates = true;
    @Column(name = "IN_APP_ORDER_UPDATES", nullable = false)
    private Boolean inAppOrderUpdates = true;
    @Column(name = "IN_APP_SHIPMENT_UPDATES", nullable = false)
    private Boolean inAppShipmentUpdates = true;
    @Column(name = "IN_APP_RETURN_REFUND_UPDATES", nullable = false)
    private Boolean inAppReturnRefundUpdates = true;
    @Column(name = "IN_APP_REVIEW_UPDATES", nullable = false)
    private Boolean inAppReviewUpdates = true;
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }
}
