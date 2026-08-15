package com.sunglassstore.service;

import com.sunglassstore.dto.request.AutomaticOfferRequest;
import com.sunglassstore.dto.request.OfferQuoteRequest;
import com.sunglassstore.dto.response.ActiveOfferResponse;
import com.sunglassstore.dto.response.AutomaticOfferResponse;
import com.sunglassstore.dto.response.OfferQuoteResponse;
import com.sunglassstore.entity.AutomaticOffer;
import com.sunglassstore.offer.AutomaticOfferPricing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AutomaticOfferService {

    // ── Administration ──────────────────────────────────────────────────────────────────────
    AutomaticOfferResponse create(AutomaticOfferRequest request, Long adminUserId);

    AutomaticOfferResponse update(Long offerId, AutomaticOfferRequest request, Long adminUserId);

    AutomaticOfferResponse setActive(Long offerId, boolean active, Long expectedVersion, Long adminUserId);

    AutomaticOfferResponse archive(Long offerId, Long adminUserId);

    Page<AutomaticOfferResponse> list(Pageable pageable);

    AutomaticOfferResponse get(Long offerId);

    // ── Storefront ──────────────────────────────────────────────────────────────────────────
    /** The offer in force right now, for the banner and eligibility labelling. */
    ActiveOfferResponse activeOffer();

    /** Prices a cart from current database state. Never trusts an amount from the caller. */
    OfferQuoteResponse quote(OfferQuoteRequest request, Long userId, String couponCode);

    // ── Used inside the order transaction ───────────────────────────────────────────────────
    /** The effective offer at a caller-supplied instant, so one transaction uses one clock. */
    Optional<AutomaticOffer> effectiveOffer(LocalDateTime now);

    /**
     * Applies {@code offer} to already-locked, already-validated order lines.
     * Separate from quote() because order creation has its own locked view of prices and stock and
     * must not re-read them.
     */
    AutomaticOfferPricing.Result priceLines(AutomaticOffer offer, List<AutomaticOfferPricing.Line> lines);

    /** Which of these variants the offer covers. Public so the order path can label its own lines. */
    java.util.Set<Long> eligibleVariantIds(AutomaticOffer offer, java.util.Map<Long, Long> variantToProduct);
}
