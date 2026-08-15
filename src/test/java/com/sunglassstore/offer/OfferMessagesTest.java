package com.sunglassstore.offer;

import com.sunglassstore.entity.AutomaticOffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banner wording, including the rule that exists because of a real incident.
 *
 * An offer was saved with the banner message "message" and the storefront displayed it — uppercased
 * by the promo strip's styling — as "MESSAGE" across the top of the shop. Nothing in the pipeline was
 * broken: the value was stored, served and rendered faithfully. These tests pin the judgement that
 * text which cannot be an offer statement is not shown to a customer at all.
 */
class OfferMessagesTest {

    private static AutomaticOffer offer(String bannerMessage, int requiredQuantity, String perGroup) {
        AutomaticOffer configured = new AutomaticOffer();
        configured.setOfferName("Test offer");
        configured.setBannerMessage(bannerMessage);
        configured.setRequiredQuantity(requiredQuantity);
        configured.setDiscountPerGroup(new BigDecimal(perGroup));
        configured.setMinimumOrderSubtotal(BigDecimal.ZERO);
        return configured;
    }

    // ── The reported defect ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the exact value that shipped as MESSAGE is replaced by generated wording")
    void theReportedPlaceholderIsNeverShown() {
        String banner = OfferMessages.banner(offer("message", 2, "500.00"));

        assertEquals("Buy any 2 eligible products and get ₹500 off automatically for every complete pair.",
                banner);
        assertFalse(banner.toLowerCase().equals("message"));
    }

    @ParameterizedTest(name = "\"{0}\" is not shown to a customer")
    @ValueSource(strings = {
            "message", "MESSAGE", "  message  ", "banner", "Banner Message", "text", "test",
            "todo", "TBD", "placeholder", "offer", "sample", "n/a", "null", "undefined",
            "asdf", "xxx", "dummy", "temp", "abc",
            "tttttt",          // the offer name typed into the field, from the same incident
            "short",           // under the length floor
            "₹500",            // an amount alone states no terms
            "Two",             // a single token
            "aVeryLongSingleTokenWithNoSpacesAtAll",
    })
    @DisplayName("placeholder, single-token and too-short values fall back to the configuration")
    void unusableMessagesFallBack(String candidate) {
        assertFalse(OfferMessages.isUsableBannerMessage(candidate), "should be rejected: " + candidate);
        assertEquals(OfferMessages.defaultBanner(offer(candidate, 2, "500.00")),
                OfferMessages.banner(offer(candidate, 2, "500.00")));
    }

    @Test
    @DisplayName("a null or blank message falls back without complaint")
    void nullAndBlankFallBack() {
        assertFalse(OfferMessages.isUsableBannerMessage(null));
        assertFalse(OfferMessages.isUsableBannerMessage("   "));
        assertEquals("Buy any 2 eligible products and get ₹500 off automatically for every complete pair.",
                OfferMessages.banner(offer(null, 2, "500.00")));
    }

    // ── Real copy is never rejected ─────────────────────────────────────────────────────────

    @ParameterizedTest(name = "\"{0}\" is shown as written")
    @ValueSource(strings = {
            "Buy any 2 eligible products and get ₹500 off automatically.",
            "Two for ₹500 — automatically applied at checkout, no code needed.",
            "Weekend pairs: ₹500 off every two frames.",
            "Two for ₹500",
    })
    @DisplayName("an administrator's real wording is used verbatim")
    void realCopyIsKept(String candidate) {
        assertTrue(OfferMessages.isUsableBannerMessage(candidate), "should be accepted: " + candidate);
        assertEquals(candidate, OfferMessages.banner(offer(candidate, 2, "500.00")));
    }

    @Test
    @DisplayName("surrounding and repeated whitespace is trimmed, not shown")
    void whitespaceIsNormalised() {
        assertEquals("Two for ₹500 this weekend only",
                OfferMessages.banner(offer("   Two  for   ₹500    this weekend only  ", 2, "500.00")));
    }

    // ── Generated wording uses the real configuration ───────────────────────────────────────

    @Test
    @DisplayName("the generated message reflects the configured quantity and amount, not ₹500 or a pair")
    void generatedWordingFollowsConfiguration() {
        assertEquals("Buy any 3 eligible products and get ₹1,250 off automatically for every complete group of 3.",
                OfferMessages.banner(offer(null, 3, "1250.00")));
        assertEquals("Buy any 5 eligible products and get ₹99.50 off automatically for every complete group of 5.",
                OfferMessages.banner(offer(null, 5, "99.50")));
    }

    @Test
    @DisplayName("terms are always generated and state the real numbers")
    void termsAlwaysGenerated() {
        // Even with usable custom copy, the terms line is the offer's own numbers — that is what stops
        // vague marketing wording from being the only statement of the offer the customer ever sees.
        assertEquals("₹500 off every 2 eligible units. Unmatched units are not discounted.",
                OfferMessages.terms(offer("Weekend pairs: big savings inside!", 2, "500.00")));
    }

    @Test
    @DisplayName("amounts use Indian digit grouping")
    void indianDigitGrouping() {
        assertEquals("₹1,250", OfferMessages.rupees(new BigDecimal("1250.00")));
        assertEquals("₹12,500", OfferMessages.rupees(new BigDecimal("12500.00")));
        assertEquals("₹1,25,000", OfferMessages.rupees(new BigDecimal("125000.00")));
        assertEquals("₹99.50", OfferMessages.rupees(new BigDecimal("99.50")));
    }
}
