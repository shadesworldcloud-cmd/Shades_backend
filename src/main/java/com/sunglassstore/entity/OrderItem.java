package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "ORDER_ITEMS")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ITEM_ID")
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    /**
     * Null once the product has been deleted from the catalogue.
     *
     * ORDER_ITEMS is the one place a deleted product's history is kept, so the column is
     * ON DELETE SET NULL rather than cascading. Everything an order needs to be displayed, totalled,
     * invoiced, returned or refunded is snapshotted on this row — productName, sku, quantity,
     * unitPrice, tax, discount and lineTotal — so a null variant costs the order nothing. Anything
     * that needs the LIVE catalogue row (restoring stock, writing a review) must null-check first.
     */
    @JoinColumn(name = "VARIANT_ID")
    private ProductVariant variant;

    @Column(name = "PRODUCT_NAME", nullable = false)
    private String productName;

    @Column(name = "SKU", nullable = false, length = 100)
    private String sku;

    /**
     * The variant's customer-facing label ("Ocean Blue") at purchase time, in the storefront's
     * precedence: colour attribute, else variant name. Part of the snapshot for the same reason
     * PRODUCT_NAME is — the live variant can be renamed, archived or deleted, and this line must
     * keep reading the way it did on the invoice. Null on lines that predate the column.
     */
    @Column(name = "VARIANT_LABEL")
    private String variantLabel;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "UNIT_PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "TAX_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "DISCOUNT_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "LINE_TOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;
}
