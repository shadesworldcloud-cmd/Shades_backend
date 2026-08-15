package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PRODUCT_ATTRIBUTES")
@Getter
@Setter
@NoArgsConstructor
public class ProductAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ATTRIBUTE_ID")
    private Long attributeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VARIANT_ID")
    private ProductVariant variant;

    @Column(name = "ATTRIBUTE_NAME", nullable = false, length = 100)
    private String attributeName;

    @Column(name = "ATTRIBUTE_VALUE", nullable = false)
    private String attributeValue;
}
