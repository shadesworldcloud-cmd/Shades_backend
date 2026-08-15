package com.sunglassstore.entity;

import com.sunglassstore.catalog.ProductSlugs;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "PRODUCTS")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_ID")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TAX_RATE_ID")
    private TaxRate taxRate;

    @Column(name = "PRODUCT_NAME", nullable = false)
    private String productName;

    /**
     * The public identifier used in storefront URLs, in place of PRODUCT_ID.
     *
     * Assigned once, when the product is created, and never touched by an ordinary update — a
     * rename must not move the product's address, or every link anyone has shared breaks silently.
     * Only an explicit slug edit by an admin changes it, and ProductServiceImpl is the only place
     * that may do so.
     *
     * Not a security boundary: it is unguessable-ish, but every endpoint still authorises. See
     * ProductSlugs.
     */
    @Column(name = "SLUG", nullable = false, unique = true, length = ProductSlugs.MAX_LENGTH)
    private String slug;

    @Column(name = "PRODUCT_DESCRIPTION", columnDefinition = "TEXT")
    private String productDescription;

    @Column(name = "BRAND", length = 150)
    private String brand;

    @Column(name = "BASE_PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    /**
     * When this product first became publicly available, in UTC. Null means it has never been
     * published, which is why a draft can never carry a New badge.
     *
     * Stamped once, on first activation, and never moved afterwards. Re-activating a delisted
     * product deliberately does NOT re-stamp it: the badge answers "is this new to the catalogue",
     * and a delist/relist cycle is not a new product. Nothing else may write this column — that is
     * the whole point of not measuring the badge from UPDATED_AT.
     */
    // Instant, not LocalDateTime. A LocalDateTime here round-trips through Connector/J with
    // serverTimezone=UTC and comes back shifted into the JVM's default zone, so comparing it
    // against LocalDateTime.now(UTC) moved the 30-day boundary by the server's offset — a product
    // published 30 days and 2 minutes ago still reported as New. An Instant has no such ambiguity:
    // it is the same point in time on both sides of the driver.
    @Column(name = "PUBLISHED_AT")
    private java.time.Instant publishedAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optimistic-locking version for the admin read-edit-save cycle. Two administrators editing
     * the same product no longer silently overwrite each other: the second save's stale version is
     * refused with a 409 (see ProductServiceImpl.updateProduct). Hibernate bumps it on every
     * update, so it also fences edits racing a deactivation.
     */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    // @OrderBy is not cosmetic here. Every surface that has to pick ONE variant to display and sell
    // — the listing card, the product page's default selection — resolves "first in-stock variant"
    // against this list, and without an explicit order Hibernate returns whatever order the join
    // happens to produce. Position is the admin-chosen family order, position 1 being the Main
    // Product; variantId breaks ties only for rows a raw-SQL writer left unpositioned.
    /**
     * @BatchSize on every association below is a measured fix, not a precaution.
     *
     * ProductResponse.fromEntity touches variants, images, categories and attributes for each
     * product, and all four are lazy — so one listing page issued a query per collection per
     * product. Measured on the 1,182-product test catalogue:
     *
     *   GET /api/products?size=200  ->  1,868 SELECT statements, 1,367 ms
     *
     * Hibernate batches the initialisation of up to 64 of these at a time instead, which turns
     * roughly 4 x 200 round trips into roughly 4 x 4.
     *
     * @BatchSize rather than a fetch join: Product holds three List collections, and fetch-joining
     * more than one bag in a single query is a MultipleBagFetchException. Paginating a fetch join
     * is also unsound — the row multiplication makes LIMIT count join rows rather than products,
     * which Hibernate can only fix by pulling the whole result set into memory.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, variantId ASC")
    @org.hibernate.annotations.BatchSize(size = 64)
    private List<ProductVariant> variants = new ArrayList<>();

    /**
     * Gallery order, decided here so every surface agrees: primary first, then DISPLAY_ORDER, then
     * IMAGE_ID.
     *
     * The IMAGE_ID tie-break is what makes it deterministic. Ordering by DISPLAY_ORDER alone left
     * images that share a value — which every multi-file upload produces, since they are all sent
     * with the same index base — in whatever order the join happened to return, so a product card
     * could show a different photo between two requests.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("isPrimary DESC, displayOrder ASC, imageId ASC")
    @org.hibernate.annotations.BatchSize(size = 64)
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 64)
    private List<ProductAttribute> attributes = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 64)
    @JoinTable(
            name = "PRODUCT_CATEGORIES",
            joinColumns = @JoinColumn(name = "PRODUCT_ID"),
            inverseJoinColumns = @JoinColumn(name = "CATEGORY_ID")
    )
    private Set<Category> categories = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // A floor, not the normal path. ProductServiceImpl.createProduct assigns the real slug —
        // derived from the name and checked for uniqueness first. This only fires for a Product
        // persisted by some other route (fixtures, a future importer), where the alternative is a
        // NOT NULL violation on SLUG. Always suffixed, because nothing has checked availability
        // here: an opaque unique-by-construction slug beats a pretty one that collides.
        if (slug == null || slug.isBlank()) slug = ProductSlugs.withFreshSuffix(ProductSlugs.toBaseSlug(productName));
        // A product created already active is published at that moment. One created as a draft
        // gets its stamp from publish() when an admin activates it.
        if (Boolean.TRUE.equals(isActive)) {
            publish();
        }
    }

    /**
     * The family's Main Product: the variant at position 1. Falls back to list order (position
     * ascending) only for a row a raw-SQL writer left unpositioned — the service never creates
     * that state.
     */
    public java.util.Optional<ProductVariant> getMainVariant() {
        return variants.stream().filter(ProductVariant::isMainVariant).findFirst()
                .or(() -> variants.stream().findFirst());
    }

    /**
     * Records first publication. Idempotent, so re-activation keeps the original date — see the
     * field comment. UTC because the New window is compared in UTC.
     */
    public void publish() {
        if (publishedAt == null) {
            publishedAt = java.time.Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
