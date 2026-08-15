package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "RETURN_ITEMS")
@Getter
@Setter
@NoArgsConstructor
public class ReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RETURN_ITEM_ID")
    private Long returnItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RETURN_ID", nullable = false)
    private ReturnRequest returnRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ITEM_ID", nullable = false)
    private OrderItem orderItem;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "ITEM_CONDITION", length = 50)
    private String itemCondition;

    @Column(name = "RETURN_REASON")
    private String returnReason;
}
