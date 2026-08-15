package com.sunglassstore.entity;

import com.sunglassstore.entity.enums.ReturnStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "RETURNS")
@Getter
@Setter
@NoArgsConstructor
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RETURN_ID")
    private Long returnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "RETURN_STATUS", nullable = false, length = 30)
    private ReturnStatus returnStatus = ReturnStatus.REQUESTED;

    @Column(name = "RETURN_REASON", nullable = false)
    private String returnReason;

    @Column(name = "CUSTOMER_COMMENTS", columnDefinition = "TEXT")
    private String customerComments;

    @Column(name = "ADMIN_COMMENTS", columnDefinition = "TEXT")
    private String adminComments;

    @Column(name = "REQUESTED_AT", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "APPROVED_AT")
    private LocalDateTime approvedAt;

    @Column(name = "RECEIVED_AT")
    private LocalDateTime receivedAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReturnItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
    }
}
