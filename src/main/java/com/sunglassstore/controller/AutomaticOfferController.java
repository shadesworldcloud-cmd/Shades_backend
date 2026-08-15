package com.sunglassstore.controller;

import com.sunglassstore.dto.request.AutomaticOfferRequest;
import com.sunglassstore.dto.request.OfferQuoteRequest;
import com.sunglassstore.dto.response.ActiveOfferResponse;
import com.sunglassstore.dto.response.AutomaticOfferResponse;
import com.sunglassstore.dto.response.OfferQuoteResponse;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.AutomaticOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Automatic quantity offer endpoints.
 *
 * Two audiences, split by path and by authorization:
 *
 *   /api/offers/automatic/active and /quote are public. The banner has to render for a signed-out
 *   visitor, and a guest cart has to be priceable — a guest has no server-side cart, so the lines
 *   arrive in the request and the server prices them from the catalogue.
 *
 *   /api/offers/automatic/admin/** requires ADMIN. Every mutation is behind @PreAuthorize on the
 *   server, independently of what the frontend chooses to render: hiding the section in the admin
 *   shell is presentation, not authorization.
 */
@RestController
@RequestMapping("/api/offers/automatic")
@RequiredArgsConstructor
public class AutomaticOfferController {

    private final AutomaticOfferService offerService;

    // ── Storefront ──────────────────────────────────────────────────────────────────────────

    /** The offer in force right now, or {active:false}. Never 404s: "no offer" is a normal answer. */
    @GetMapping("/active")
    public ResponseEntity<ActiveOfferResponse> activeOffer() {
        return ResponseEntity.ok(offerService.activeOffer());
    }

    /**
     * Prices a cart. POST rather than GET because the cart is the body — and because a cart in a
     * query string would end up in access logs and browser history.
     *
     * `principal` is optional: signed in, a coupon code can also be compared against the automatic
     * offer; signed out, the automatic offer is quoted on its own.
     */
    @PostMapping("/quote")
    public ResponseEntity<OfferQuoteResponse> quote(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) String couponCode,
            @Valid @RequestBody OfferQuoteRequest request) {
        Long userId = principal == null ? null : principal.getUserId();
        return ResponseEntity.ok(offerService.quote(request, userId, couponCode));
    }

    // ── Administration ──────────────────────────────────────────────────────────────────────

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AutomaticOfferResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(offerService.list(pageable));
    }

    @GetMapping("/admin/{offerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AutomaticOfferResponse> get(@PathVariable Long offerId) {
        return ResponseEntity.ok(offerService.get(offerId));
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AutomaticOfferResponse> create(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody AutomaticOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(offerService.create(request, principal.getUserId()));
    }

    /** 409 when the offer has moved on since the administrator loaded it. */
    @PutMapping("/admin/{offerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AutomaticOfferResponse> update(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long offerId,
            @Valid @RequestBody AutomaticOfferRequest request) {
        return ResponseEntity.ok(offerService.update(offerId, request, principal.getUserId()));
    }

    @PatchMapping("/admin/{offerId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AutomaticOfferResponse> setActive(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long offerId,
            @RequestParam boolean active,
            @RequestParam Long version) {
        return ResponseEntity.ok(offerService.setActive(offerId, active, version, principal.getUserId()));
    }

    @DeleteMapping("/admin/{offerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AutomaticOfferResponse> archive(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long offerId) {
        return ResponseEntity.ok(offerService.archive(offerId, principal.getUserId()));
    }
}
