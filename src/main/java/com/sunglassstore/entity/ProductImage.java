package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PRODUCT_IMAGES")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IMAGE_ID")
    private Long imageId;

    /**
     * The identifier public responses carry, so a gallery does not publish IMAGE_ID — the same
     * reasoning as Product.slug. Admin endpoints still address images by IMAGE_ID: those callers
     * are authorised, and an internal id is not a secret to them.
     */
    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    /**
     * The colourway this photograph shows, or null for a general product photo.
     *
     * This used to be recovered by running a regex over IMAGE_URL for "/variants/(\d+)/", which put
     * a data relationship inside a storage path — see the migration comment. A real foreign key
     * means the database rejects an image pointing at a variant of a different product, and that a
     * deleted variant demotes its photos to general images (ON DELETE SET NULL) rather than
     * orphaning a dangling id.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VARIANT_ID")
    private ProductVariant variant;

    @Column(name = "IMAGE_URL", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "ALT_TEXT")
    private String altText;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder = 0;

    /**
     * At most one per product, enforced by the database through the generated PRIMARY_SINGLETON
     * column under UQ_PRODUCT_IMAGES_PRIMARY. That column is deliberately not mapped here: it is
     * derived, Hibernate must never try to write it, and nothing in Java needs to read it.
     */
    @Column(name = "IS_PRIMARY", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (publicId == null) publicId = UUID.randomUUID().toString();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Null-safe accessor: the variant association is lazy and optional at every call site. */
    public Long getVariantId() {
        return variant == null ? null : variant.getVariantId();
    }
}
