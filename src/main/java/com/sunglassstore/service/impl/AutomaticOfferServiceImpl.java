package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.AutomaticOfferRequest;
import com.sunglassstore.dto.request.OfferQuoteRequest;
import com.sunglassstore.dto.request.ValidateCouponRequest;
import com.sunglassstore.dto.response.ActiveOfferResponse;
import com.sunglassstore.dto.response.AutomaticOfferResponse;
import com.sunglassstore.dto.response.CouponValidationResponse;
import com.sunglassstore.dto.response.OfferQuoteResponse;
import com.sunglassstore.entity.AutomaticOffer;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.enums.OfferScopeType;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.OptimisticLockConflictException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.offer.AutomaticOfferPricing;
import com.sunglassstore.offer.MerchandisePromotionPolicy;
import com.sunglassstore.offer.OfferMessages;
import com.sunglassstore.offer.OrderTotals;
import com.sunglassstore.repository.AutomaticOfferRepository;
import com.sunglassstore.repository.CategoryRepository;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.ProductVariantRepository;
import com.sunglassstore.service.AutomaticOfferService;
import com.sunglassstore.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Automatic quantity offer: administration, storefront reads, and the pricing entry points used
 * inside the order transaction.
 *
 * The arithmetic itself is not here — it is in AutomaticOfferPricing, which has no dependencies and
 * is tested exhaustively. This class is the part that needs a database: resolving which offer is in
 * force, which units it covers, and what the whole cart therefore costs.
 */
@Service
@RequiredArgsConstructor
public class AutomaticOfferServiceImpl implements AutomaticOfferService {

    private final AutomaticOfferRepository offerRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CouponService couponService;

    // ── Administration ──────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AutomaticOfferResponse create(AutomaticOfferRequest request, Long adminUserId) {
        AutomaticOffer offer = new AutomaticOffer();
        applyRequest(offer, request, adminUserId);
        offer.setCreatedByUserId(adminUserId);
        boolean activating = Boolean.TRUE.equals(request.getIsActive());
        offer.setIsActive(activating);
        if (activating) {
            refuseIfAnotherOfferIsActive(null);
        }
        return AutomaticOfferResponse.fromEntity(saved(offer), LocalDateTime.now());
    }

    @Override
    @Transactional
    public AutomaticOfferResponse update(Long offerId, AutomaticOfferRequest request, Long adminUserId) {
        AutomaticOffer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Automatic offer not found: " + offerId));
        if (offer.getArchivedAt() != null) {
            throw new ConflictException("This offer has been archived and can no longer be edited.");
        }
        requireVersion(offer, request.getVersion());

        boolean wasActive = Boolean.TRUE.equals(offer.getIsActive());
        boolean activating = request.getIsActive() == null ? wasActive : request.getIsActive();
        if (activating && !wasActive) {
            refuseIfAnotherOfferIsActive(offerId);
        }
        applyRequest(offer, request, adminUserId);
        offer.setIsActive(activating);
        return AutomaticOfferResponse.fromEntity(saved(offer), LocalDateTime.now());
    }

    @Override
    @Transactional
    public AutomaticOfferResponse setActive(Long offerId, boolean active, Long expectedVersion, Long adminUserId) {
        AutomaticOffer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Automatic offer not found: " + offerId));
        if (offer.getArchivedAt() != null) {
            throw new ConflictException("This offer has been archived and can no longer be activated.");
        }
        requireVersion(offer, expectedVersion);
        if (active && !Boolean.TRUE.equals(offer.getIsActive())) {
            refuseIfAnotherOfferIsActive(offerId);
        }
        offer.setIsActive(active);
        offer.setUpdatedByUserId(adminUserId);
        return AutomaticOfferResponse.fromEntity(saved(offer), LocalDateTime.now());
    }

    @Override
    @Transactional
    public AutomaticOfferResponse archive(Long offerId, Long adminUserId) {
        AutomaticOffer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Automatic offer not found: " + offerId));
        // Archive, never delete. Orders hold a snapshot rather than a join, so a deletion would not
        // corrupt history — but it would lose the audit trail of what was once offered, and the
        // ORDERS.AUTO_OFFER_ID reference exists precisely to be followed.
        offer.setIsActive(false);
        offer.setArchivedAt(LocalDateTime.now());
        offer.setUpdatedByUserId(adminUserId);
        return AutomaticOfferResponse.fromEntity(saved(offer), LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AutomaticOfferResponse> list(Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return offerRepository.findAllByOrderByAutomaticOfferIdDesc(pageable)
                .map(offer -> AutomaticOfferResponse.fromEntity(offer, now));
    }

    @Override
    @Transactional(readOnly = true)
    public AutomaticOfferResponse get(Long offerId) {
        return offerRepository.findById(offerId)
                .map(offer -> AutomaticOfferResponse.fromEntity(offer, LocalDateTime.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Automatic offer not found: " + offerId));
    }

    /**
     * The database's UNIQUE index on the generated ACTIVE_SINGLETON column is the real guarantee.
     * This read exists to turn its duplicate-key error into a 409 that names the offer holding the
     * slot, which is a message an administrator can act on. The row lock taken by the caller's
     * findByIdForUpdate plus this check inside the same transaction is what makes two simultaneous
     * activations resolve as one success and one conflict rather than two successes.
     */
    private void refuseIfAnotherOfferIsActive(Long exceptOfferId) {
        offerRepository.findActive()
                .filter(active -> exceptOfferId == null || !active.getAutomaticOfferId().equals(exceptOfferId))
                .ifPresent(active -> {
                    throw new ConflictException("\"" + active.getOfferName()
                            + "\" is already the active automatic offer. Deactivate it first — only one "
                            + "automatic offer can be live at a time.");
                });
    }

    /**
     * Saves and flushes, so the response carries the version the database now holds.
     *
     * A plain save() returns before the flush, and @Version is incremented at flush time — so the
     * response carried the *pre-write* version. The admin UI round-trips that value into its next
     * request, which meant two consecutive edits, or an activate followed by a deactivate, answered
     * 409 "updated elsewhere" when nobody else had touched anything. Found by the E2E toggle test.
     */
    private AutomaticOffer saved(AutomaticOffer offer) {
        return offerRepository.saveAndFlush(offer);
    }

    private void requireVersion(AutomaticOffer offer, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new BadRequestException(
                    "A version is required so a concurrent edit cannot be overwritten. Reload the offer and try again.");
        }
        if (!expectedVersion.equals(offer.getVersion())) {
            throw new OptimisticLockConflictException(
                    "This offer was updated elsewhere. Refresh and review the latest version before trying again.");
        }
    }

    private void applyRequest(AutomaticOffer offer, AutomaticOfferRequest request, Long adminUserId) {
        OfferScopeType scope;
        try {
            scope = OfferScopeType.valueOf(request.getScopeType().trim().toUpperCase());
        } catch (IllegalArgumentException notAScope) {
            throw new BadRequestException("Unknown eligibility scope: " + request.getScopeType());
        }
        if (!request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new BadRequestException("The offer's end date and time must be later than its start.");
        }

        Set<Long> productIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        if (scope == OfferScopeType.SELECTED_PRODUCTS) {
            productIds = validIds(request.getProductIds(), "product",
                    ids -> productRepository.findAllById(ids).stream()
                            .map(com.sunglassstore.entity.Product::getProductId).toList());
        } else if (scope == OfferScopeType.SELECTED_CATEGORIES) {
            categoryIds = validIds(request.getCategoryIds(), "category",
                    ids -> categoryRepository.findAllById(ids).stream()
                            .map(com.sunglassstore.entity.Category::getCategoryId).toList());
        }

        offer.setOfferName(sanitiseDisplayText(request.getOfferName(), 120));
        offer.setBannerMessage(sanitiseDisplayText(request.getBannerMessage(), 300));
        offer.setRequiredQuantity(request.getRequiredQuantity());
        offer.setDiscountPerGroup(request.getDiscountPerGroup().setScale(2, java.math.RoundingMode.UNNECESSARY));
        offer.setMinimumOrderSubtotal(request.getMinimumOrderSubtotal() == null
                ? BigDecimal.ZERO.setScale(2)
                : request.getMinimumOrderSubtotal().setScale(2, java.math.RoundingMode.UNNECESSARY));
        offer.setScopeType(scope);
        offer.setProductIds(productIds);
        offer.setCategoryIds(categoryIds);
        offer.setStartsAt(request.getStartsAt());
        offer.setEndsAt(request.getEndsAt());
        offer.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        offer.setUpdatedByUserId(adminUserId);
    }

    private Set<Long> validIds(Set<Long> requested, String label,
                               java.util.function.Function<Set<Long>, List<Long>> existing) {
        if (requested == null || requested.isEmpty()) {
            throw new BadRequestException("Select at least one " + label + " for this eligibility scope.");
        }
        Set<Long> found = new HashSet<>(existing.apply(requested));
        Set<Long> missing = new TreeSet<>(requested);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new BadRequestException("Unknown " + label + " reference(s): " + missing);
        }
        return found;
    }

    /**
     * Administrator display text is stored as plain text, never markup.
     *
     * Tags are removed rather than escaped: the frontend renders these as text nodes, so an escaped
     * "&lt;b&gt;" would simply be displayed literally in the banner, which is worse than dropping
     * it. Control characters go too — a newline in a one-line banner strip is a layout bug.
     */
    private String sanitiseDisplayText(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw
                // Script and style elements go with their contents. Stripping only the tags left the
                // body behind as text — harmless, since the frontend renders text nodes and never
                // interprets HTML, but "alert(1)" in a shop banner is not what anyone meant. Anything
                // else keeps its readable text: "<b>Two for ₹500</b>" should still say Two for ₹500.
                .replaceAll("(?is)<\\s*(script|style)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>", " ")
                // An unclosed <script> would otherwise survive the pair-matching pass above.
                .replaceAll("(?is)<\\s*(script|style)\\b.*", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("[\\p{Cntrl}]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength).trim() : cleaned;
    }

    // ── Storefront ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ActiveOfferResponse activeOffer() {
        return effectiveOffer(LocalDateTime.now())
                .map(ActiveOfferResponse::fromEntity)
                .orElse(ActiveOfferResponse.NONE);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AutomaticOffer> effectiveOffer(LocalDateTime now) {
        List<AutomaticOffer> effective = offerRepository.findEffective(now, PageRequest.of(0, 1));
        return effective.isEmpty() ? Optional.empty() : Optional.of(effective.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public OfferQuoteResponse quote(OfferQuoteRequest request, Long userId, String couponCode) {
        LocalDateTime now = LocalDateTime.now();
        List<OfferQuoteRequest.QuoteLine> requested =
                request == null || request.getLines() == null ? List.of() : request.getLines();

        // Merge duplicate lines for the same variant before pricing. A client that sends the same
        // variant twice must get the same answer as one that sends it once with the summed
        // quantity — the calculation counts units, and this is where that is made true of the input.
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OfferQuoteRequest.QuoteLine line : requested) {
            if (line.getVariantId() == null || line.getQuantity() == null || line.getQuantity() <= 0) {
                continue;
            }
            quantities.merge(line.getVariantId(), line.getQuantity(), Integer::sum);
        }

        List<Long> unresolved = new ArrayList<>();
        List<AutomaticOfferPricing.Line> priced = new ArrayList<>();
        Map<Long, Long> variantToProduct = new HashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        int itemQuantity = 0;

        if (!quantities.isEmpty()) {
            Map<Long, ProductVariant> variants = new HashMap<>();
            for (ProductVariant variant : variantRepository.findAllById(quantities.keySet())) {
                variants.put(variant.getVariantId(), variant);
            }
            for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
                ProductVariant variant = variants.get(entry.getKey());
                int quantity = entry.getValue();
                // Anything checkout could not fulfil is excluded from every figure below. Quoting a
                // discount on units that cannot be bought is exactly the mismatch that makes the
                // final total differ from the estimate.
                if (variant == null || !Boolean.TRUE.equals(variant.getIsActive())
                        || !Boolean.TRUE.equals(variant.getProduct().getIsActive())
                        || variant.getPrice() == null
                        || variant.getQuantityAvailable() == null
                        || variant.getQuantityAvailable() < quantity) {
                    unresolved.add(entry.getKey());
                    continue;
                }
                subtotal = subtotal.add(variant.getPrice().multiply(BigDecimal.valueOf(quantity)));
                itemQuantity += quantity;
                variantToProduct.put(variant.getVariantId(), variant.getProduct().getProductId());
                priced.add(new AutomaticOfferPricing.Line(variant.getVariantId(), quantity,
                        variant.getPrice(), true));
            }
        }

        Optional<AutomaticOffer> effective = effectiveOffer(now);
        Set<Long> eligibleVariantIds = effective
                .map(offer -> eligibleVariantIds(offer, variantToProduct))
                .orElse(Set.of());
        List<AutomaticOfferPricing.Line> scoped = priced.stream()
                .map(line -> new AutomaticOfferPricing.Line(line.variantId(), line.quantity(),
                        line.unitPrice(), eligibleVariantIds.contains(line.variantId())))
                .toList();

        AutomaticOfferPricing.Result automatic = effective
                .map(offer -> AutomaticOfferPricing.calculate(scoped, offer.getRequiredQuantity(),
                        offer.getDiscountPerGroup(), offer.getMinimumOrderSubtotal()))
                .orElse(AutomaticOfferPricing.NONE);

        BigDecimal couponDiscount = BigDecimal.ZERO;
        String couponLabel = null;
        if (couponCode != null && !couponCode.isBlank() && userId != null) {
            // A coupon that no longer validates is simply not part of the comparison. It is not an
            // error here: the customer is looking at their bag, and the coupon field reports its own
            // failures. Order creation validates it again and does reject.
            try {
                ValidateCouponRequest validate = new ValidateCouponRequest();
                validate.setCouponCode(couponCode.trim());
                CouponValidationResponse validated = couponService.validateCoupon(userId, validate);
                couponDiscount = validated.getCalculatedDiscount() == null
                        ? BigDecimal.ZERO : validated.getCalculatedDiscount();
                couponLabel = "Coupon " + validated.getCouponCode();
            } catch (RuntimeException rejected) {
                couponDiscount = BigDecimal.ZERO;
            }
        }

        String automaticLabel = effective.map(AutomaticOffer::getOfferName).orElse(null);
        MerchandisePromotionPolicy.Decision decision = MerchandisePromotionPolicy.decide(
                automatic.discount(), automaticLabel, couponDiscount, couponLabel);
        OrderTotals totals = OrderTotals.of(subtotal, decision.discount());

        OfferQuoteResponse.AutomaticOfferQuote offerQuote = effective
                .map(offer -> new OfferQuoteResponse.AutomaticOfferQuote(
                        offer.getAutomaticOfferId(),
                        offer.getOfferName(),
                        OfferMessages.terms(offer),
                        offer.getRequiredQuantity(),
                        offer.getDiscountPerGroup(),
                        automatic.eligibleQuantity(),
                        automatic.eligibleSubtotal(),
                        automatic.completeGroups(),
                        automatic.discount(),
                        automatic.unitsToNextGroup(),
                        progressMessage(offer, automatic),
                        new TreeSet<>(eligibleVariantIds)))
                .orElse(null);

        return new OfferQuoteResponse(totals.subtotal(), itemQuantity, totals.discount(),
                totals.taxable(), totals.tax(), totals.shipping(), totals.total(),
                decision.applied().name(), decision.label(), decision.suppressedLabel(),
                decision.reason(), offerQuote, unresolved,
                diagnose(now, effective.orElse(null), automatic, eligibleVariantIds, decision));
    }

    /**
     * Why the cart did or did not get an automatic discount.
     *
     * Every branch names a condition someone can check. "The offer is not applying" was reported
     * against a working system, and there was no way to tell from the response whether the offer was
     * scheduled, expired, out of scope, or simply hadn't reached a full group yet — so the answer is
     * now in the payload rather than in whoever's head last read the resolver.
     *
     * Safe to expose: it repeats configuration the storefront already shows and never mentions users,
     * carts, prices of other customers, or internals.
     */
    private String diagnose(LocalDateTime now,
                            AutomaticOffer effective,
                            AutomaticOfferPricing.Result result,
                            Set<Long> eligibleVariantIds,
                            MerchandisePromotionPolicy.Decision decision) {
        if (decision.applied() == MerchandisePromotionPolicy.AppliedPromotion.AUTOMATIC_OFFER) {
            return null;
        }
        if (decision.applied() == MerchandisePromotionPolicy.AppliedPromotion.COUPON) {
            return "A coupon gave a larger discount than the automatic offer, so the coupon was applied.";
        }
        if (effective == null) {
            // Distinguish "none configured" from "one exists but is not in force", which is the
            // distinction that actually matters when an administrator says it should be live.
            return offerRepository.findActive()
                    .map(active -> {
                        if (active.getStartsAt().isAfter(now)) {
                            return "\"" + active.getOfferName() + "\" is active but scheduled to start at "
                                    + active.getStartsAt() + "; the store clock reads " + now + ".";
                        }
                        if (!active.getEndsAt().isAfter(now)) {
                            return "\"" + active.getOfferName() + "\" is active but ended at "
                                    + active.getEndsAt() + "; the store clock reads " + now + ".";
                        }
                        return "\"" + active.getOfferName() + "\" is active but did not resolve as effective.";
                    })
                    .orElse("No automatic offer is currently active.");
        }
        if (eligibleVariantIds.isEmpty()) {
            return "\"" + effective.getOfferName() + "\" is live, but nothing in this bag is within its "
                    + "eligibility scope (" + effective.getScopeType().name() + ").";
        }
        if (effective.getMinimumOrderSubtotal() != null
                && effective.getMinimumOrderSubtotal().compareTo(BigDecimal.ZERO) > 0
                && result.eligibleSubtotal().compareTo(effective.getMinimumOrderSubtotal()) < 0) {
            return "\"" + effective.getOfferName() + "\" needs a minimum eligible subtotal of "
                    + OfferMessages.rupees(effective.getMinimumOrderSubtotal()) + "; this bag holds "
                    + OfferMessages.rupees(result.eligibleSubtotal()) + " of eligible items.";
        }
        return "\"" + effective.getOfferName() + "\" is live, but this bag holds "
                + result.eligibleQuantity() + " eligible unit" + (result.eligibleQuantity() == 1 ? "" : "s")
                + " and a complete group needs " + effective.getRequiredQuantity() + ".";
    }

    /**
     * "Add 1 more eligible item to receive another ₹500 discount."
     *
     * Generated from the same Result the discount came from, so the sentence and the number can
     * never disagree. Null when there is nothing useful to say — a cart with no eligible items at
     * all is not making progress toward anything, and a nudge there would be noise.
     */
    private String progressMessage(AutomaticOffer offer, AutomaticOfferPricing.Result result) {
        if (result.eligibleQuantity() == 0) {
            return null;
        }
        if (offer.getMinimumOrderSubtotal() != null
                && offer.getMinimumOrderSubtotal().compareTo(BigDecimal.ZERO) > 0
                && result.eligibleSubtotal().compareTo(offer.getMinimumOrderSubtotal()) < 0) {
            BigDecimal shortfall = offer.getMinimumOrderSubtotal().subtract(result.eligibleSubtotal());
            return "Add " + OfferMessages.rupees(shortfall) + " more of eligible items to unlock this offer.";
        }
        int needed = result.unitsToNextGroup();
        return "Add " + needed + (needed == 1 ? " more eligible item" : " more eligible items")
                + " to receive another " + OfferMessages.rupees(offer.getDiscountPerGroup()) + " discount.";
    }

    // ── Shared with the order transaction ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Set<Long> eligibleVariantIds(AutomaticOffer offer, Map<Long, Long> variantToProduct) {
        if (offer == null || variantToProduct == null || variantToProduct.isEmpty()) {
            return Set.of();
        }
        return switch (offer.getScopeType()) {
            case ALL_PRODUCTS -> new HashSet<>(variantToProduct.keySet());
            case SELECTED_PRODUCTS -> variantToProduct.entrySet().stream()
                    .filter(entry -> offer.getProductIds().contains(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            case SELECTED_CATEGORIES -> {
                if (offer.getCategoryIds().isEmpty()) {
                    yield Set.of();
                }
                Set<Long> inScope = new HashSet<>(productRepository.findProductIdsInCategories(
                        new HashSet<>(variantToProduct.values()), offer.getCategoryIds()));
                yield variantToProduct.entrySet().stream()
                        .filter(entry -> inScope.contains(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet());
            }
        };
    }

    @Override
    public AutomaticOfferPricing.Result priceLines(AutomaticOffer offer, List<AutomaticOfferPricing.Line> lines) {
        if (offer == null) {
            return AutomaticOfferPricing.NONE;
        }
        return AutomaticOfferPricing.calculate(lines, offer.getRequiredQuantity(),
                offer.getDiscountPerGroup(), offer.getMinimumOrderSubtotal());
    }
}
