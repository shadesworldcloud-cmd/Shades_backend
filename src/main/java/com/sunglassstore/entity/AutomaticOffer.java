package com.sunglassstore.entity;

import com.sunglassstore.entity.enums.OfferScopeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * An automatic quantity offer: every complete group of REQUIRED_QUANTITY eligible units takes
 * DISCOUNT_PER_GROUP off the merchandise subtotal, with no coupon code involved.
 *
 * Nothing here is a display string that the pricing rule does not also honour. BANNER_MESSAGE is
 * presentation only and may be null, in which case the API generates the wording from the numbers
 * below — that is what stops a stale hand-written banner from contradicting the maths.
 *
 * @Version is Hibernate-managed and appropriate here for the reason it is not on User: an offer is
 * only ever written by an administrator's read-edit-save cycle, so a version bump always
 * corresponds to a real edit and a conflict always means a second administrator got there first.
 */
@Entity
@Table(name = "AUTOMATIC_OFFERS")
@Getter
@Setter
@NoArgsConstructor
public class AutomaticOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUTOMATIC_OFFER_ID")
    private Long automaticOfferId;

    @Column(name = "OFFER_NAME", nullable = false, length = 120)
    private String offerName;

    @Column(name = "BANNER_MESSAGE", length = 300)
    private String bannerMessage;

    @Column(name = "REQUIRED_QUANTITY", nullable = false)
    private Integer requiredQuantity;

    @Column(name = "DISCOUNT_PER_GROUP", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountPerGroup;

    @Column(name = "MINIMUM_ORDER_SUBTOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumOrderSubtotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "SCOPE_TYPE", nullable = false, length = 24)
    private OfferScopeType scopeType = OfferScopeType.ALL_PRODUCTS;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = false;

    @Column(name = "STARTS_AT", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ENDS_AT", nullable = false)
    private LocalDateTime endsAt;

    /**
     * Tiebreaker for the effective-offer query. The database already allows only one active offer,
     * so this never decides anything today; it exists so that if the singleton rule is ever
     * relaxed, selection stays deterministic instead of depending on row order.
     */
    @Column(name = "PRIORITY", nullable = false)
    private Integer priority = 0;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version = 0L;

    /** Archive rather than delete: orders reference the offer, and history must stay readable. */
    @Column(name = "ARCHIVED_AT")
    private LocalDateTime archivedAt;

    @Column(name = "CREATED_BY_USER_ID")
    private Long createdByUserId;

    @Column(name = "UPDATED_BY_USER_ID")
    private Long updatedByUserId;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "AUTOMATIC_OFFER_PRODUCTS",
            joinColumns = @JoinColumn(name = "AUTOMATIC_OFFER_ID"))
    @Column(name = "PRODUCT_ID")
    private Set<Long> productIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "AUTOMATIC_OFFER_CATEGORIES",
            joinColumns = @JoinColumn(name = "AUTOMATIC_OFFER_ID"))
    @Column(name = "CATEGORY_ID")
    private Set<Long> categoryIds = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
