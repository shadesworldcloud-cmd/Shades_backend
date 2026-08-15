package com.sunglassstore.offer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stacking decision.
 *
 * Worth its own tests rather than only being covered through a cart, because the property that
 * matters most is a negative one — the two discounts are never added together — and the cheapest
 * place to prove that exhaustively is here.
 */
class MerchandisePromotionPolicyTest {

    private static final String OFFER = "Weekend pair offer";
    private static final String COUPON = "Coupon SAVE200";

    private static MerchandisePromotionPolicy.Decision decide(String automatic, String coupon) {
        return MerchandisePromotionPolicy.decide(
                automatic == null ? null : new BigDecimal(automatic), OFFER,
                coupon == null ? null : new BigDecimal(coupon), COUPON);
    }

    @Test
    @DisplayName("neither qualifying means no discount and nothing to explain")
    void neitherQualifies() {
        MerchandisePromotionPolicy.Decision decision = decide("0.00", "0.00");

        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.NONE, decision.applied());
        assertEquals(BigDecimal.ZERO.setScale(2), decision.discount());
        assertNull(decision.reason());
        assertNull(decision.suppressedLabel());
    }

    @Test
    @DisplayName("the automatic offer alone applies with nothing suppressed")
    void automaticAlone() {
        MerchandisePromotionPolicy.Decision decision = decide("1000.00", null);

        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.AUTOMATIC_OFFER, decision.applied());
        assertEquals(new BigDecimal("1000.00"), decision.discount());
        assertEquals(OFFER, decision.label());
        assertNull(decision.suppressedLabel(), "nothing was competing");
    }

    @Test
    @DisplayName("the coupon alone applies with nothing suppressed")
    void couponAlone() {
        MerchandisePromotionPolicy.Decision decision = decide("0.00", "200.00");

        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.COUPON, decision.applied());
        assertEquals(new BigDecimal("200.00"), decision.discount());
        assertNull(decision.suppressedLabel());
    }

    @Test
    @DisplayName("the larger discount wins and the customer is told why the other did not apply")
    void largerWinsAndTheOtherIsExplained() {
        MerchandisePromotionPolicy.Decision offerWins = decide("1000.00", "200.00");
        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.AUTOMATIC_OFFER, offerWins.applied());
        assertEquals(new BigDecimal("1000.00"), offerWins.discount());
        assertEquals(COUPON, offerWins.suppressedLabel());
        assertNotNull(offerWins.reason());
        assertTrue(offerWins.reason().contains(COUPON), "the suppressed promotion is named");
        assertTrue(offerWins.reason().toLowerCase().contains("cannot be combined"));

        MerchandisePromotionPolicy.Decision couponWins = decide("1000.00", "2500.00");
        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.COUPON, couponWins.applied());
        assertEquals(new BigDecimal("2500.00"), couponWins.discount());
        assertEquals(OFFER, couponWins.suppressedLabel());
        assertTrue(couponWins.reason().contains(OFFER));
    }

    @Test
    @DisplayName("they are never added together")
    void neverStacked() {
        for (String[] pair : new String[][] {
                {"1000.00", "200.00"}, {"200.00", "1000.00"}, {"500.00", "500.00"},
                {"0.01", "999.99"}, {"12345.67", "12345.66"} }) {
            MerchandisePromotionPolicy.Decision decision = decide(pair[0], pair[1]);
            BigDecimal sum = new BigDecimal(pair[0]).add(new BigDecimal(pair[1]));
            BigDecimal larger = new BigDecimal(pair[0]).max(new BigDecimal(pair[1]));

            assertEquals(larger, decision.discount(), pair[0] + " vs " + pair[1]);
            assertTrue(decision.discount().compareTo(sum) < 0,
                    "the applied discount must be one of them, never the sum");
        }
    }

    @Test
    @DisplayName("a tie goes to the automatic offer, which the customer did nothing to earn or lose")
    void tieGoesToTheAutomaticOffer() {
        MerchandisePromotionPolicy.Decision decision = decide("500.00", "500.00");

        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.AUTOMATIC_OFFER, decision.applied());
        assertEquals(new BigDecimal("500.00"), decision.discount());
        assertEquals(COUPON, decision.suppressedLabel(), "the coupon is still explained");
    }

    @Test
    @DisplayName("the result does not depend on which side was computed first")
    void orderIndependent() {
        // Same two amounts, swapped between the parameters: the winner's *amount* must be identical.
        MerchandisePromotionPolicy.Decision oneWay = MerchandisePromotionPolicy.decide(
                new BigDecimal("300.00"), OFFER, new BigDecimal("900.00"), COUPON);
        MerchandisePromotionPolicy.Decision otherWay = MerchandisePromotionPolicy.decide(
                new BigDecimal("900.00"), OFFER, new BigDecimal("300.00"), COUPON);

        assertEquals(new BigDecimal("900.00"), oneWay.discount());
        assertEquals(new BigDecimal("900.00"), otherWay.discount());
        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.COUPON, oneWay.applied());
        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.AUTOMATIC_OFFER, otherWay.applied());
    }

    @Test
    @DisplayName("a negative or null amount is treated as no discount, never as a credit")
    void negativeAndNullAreNotDiscounts() {
        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.NONE, decide(null, null).applied());
        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.NONE,
                decide("-500.00", "-1.00").applied());
        MerchandisePromotionPolicy.Decision decision = decide("-500.00", "250.00");
        assertEquals(MerchandisePromotionPolicy.AppliedPromotion.COUPON, decision.applied());
        assertEquals(new BigDecimal("250.00"), decision.discount());
        assertNull(decision.suppressedLabel(), "a negative amount was never a competitor");
    }
}
