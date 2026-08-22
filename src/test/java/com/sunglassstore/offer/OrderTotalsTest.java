package com.sunglassstore.offer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tax, shipping and total.
 *
 * These rules were inlined in OrderServiceImpl and are now shared with the cart quote. The tests
 * that matter are the ones pinning the behaviour that was preserved rather than changed — notably
 * that shipping is decided by the pre-discount subtotal, so introducing the offer did not quietly
 * move the free-shipping line for discounted orders.
 *
 * Prices are tax-inclusive: the administrator's price is what the customer pays, and GST is
 * extracted from it. The headline assertion is priceIsTheTotal() — everything else here exists to
 * stop that being true by accident.
 */
class OrderTotalsTest {

    @Test
    @DisplayName("the price the administrator set is exactly what the customer pays")
    void priceIsTheTotal() {
        // The whole point of the change. ₹5,000 of merchandise, no discount, free shipping: the
        // customer is charged ₹5,000, not ₹5,900. Tax is disclosed inside that figure.
        OrderTotals totals = OrderTotals.of(new BigDecimal("5000.00"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("5000.00"), totals.total(), "the shelf price IS the total");
        assertEquals(new BigDecimal("4237.29"), totals.taxable(), "net of 18% GST");
        assertEquals(new BigDecimal("762.71"), totals.tax(), "the GST inside the price");
    }

    @Test
    @DisplayName("tax is extracted from the discounted amount, not added to it")
    void taxFollowsTheDiscount() {
        OrderTotals totals = OrderTotals.of(new BigDecimal("5000.00"), new BigDecimal("1000.00"));

        // ₹4,000 changes hands for merchandise. 4000 / 1.18 = 3389.8305…, half-up to ₹3,389.83,
        // and the tax is the ₹610.17 remainder. Under the old additive rule this order totalled
        // ₹4,720.
        assertEquals(new BigDecimal("3389.83"), totals.taxable());
        assertEquals(new BigDecimal("610.17"), totals.tax());
        assertEquals(new BigDecimal("4000.00"), totals.total());
        assertEquals(BigDecimal.ZERO.setScale(2), totals.shipping(), "free above ₹500");
    }

    @Test
    @DisplayName("taxable plus tax always equals the merchandise the customer pays for")
    void taxAndTaxableAlwaysFootToTheGrossAmount() {
        // The invariant a GST invoice depends on. Swept rather than sampled: a single example
        // passes under both the correct derivation and the subtly wrong one below.
        for (long paise = 0; paise <= 2_000_00L; paise += 7) {
            BigDecimal gross = BigDecimal.valueOf(paise, 2);
            OrderTotals totals = OrderTotals.of(gross, BigDecimal.ZERO);

            assertEquals(gross, totals.taxable().add(totals.tax()),
                    "taxable + tax must be the gross amount at ₹" + gross.toPlainString());
            assertEquals(totals.taxable().add(totals.tax()).add(totals.shipping()), totals.total(),
                    "the total must foot at ₹" + gross.toPlainString());
        }
    }

    @Test
    @DisplayName("tax is the remainder, not 18% of the net — the two differ by a paisa")
    void taxIsDerivedBySubtractionNotMultiplication() {
        // ₹513 is one of 685 whole-rupee prices between ₹500 and ₹5,000 where the two derivations
        // disagree. Net is ₹434.75. Taking 18% of that net gives ₹78.26, which would make the
        // invoice claim ₹513.01 — a paisa more than the customer was charged. The remainder is
        // ₹78.25 and foots exactly. This test is what stops someone "simplifying" the arithmetic
        // back to a multiplication.
        OrderTotals totals = OrderTotals.of(new BigDecimal("513.00"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("434.75"), totals.taxable());
        assertEquals(new BigDecimal("78.25"), totals.tax(), "the remainder, not 78.26");
        assertEquals(new BigDecimal("513.00"), totals.total());
    }

    @Test
    @DisplayName("free shipping is decided by the subtotal before the discount")
    void shippingIgnoresTheDiscount() {
        // ₹600 of merchandise discounted to ₹100 keeps free shipping, because the customer did buy
        // ₹600 of goods. Deciding it on the discounted figure would charge ₹49 more the moment an
        // offer applied, which is the opposite of what an offer is for.
        OrderTotals discounted = OrderTotals.of(new BigDecimal("600.00"), new BigDecimal("500.00"));
        assertEquals(BigDecimal.ZERO.setScale(2), discounted.shipping());

        OrderTotals under = OrderTotals.of(new BigDecimal("499.99"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("49.00"), under.shipping());

        OrderTotals exactly = OrderTotals.of(new BigDecimal("500.00"), BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO.setScale(2), exactly.shipping(), "the threshold is inclusive");
    }

    @Test
    @DisplayName("a discount cannot exceed the subtotal, and the total never goes negative")
    void totalNeverGoesNegative() {
        OrderTotals totals = OrderTotals.of(new BigDecimal("400.00"), new BigDecimal("9999.00"));

        assertEquals(new BigDecimal("400.00"), totals.discount(), "clamped to the merchandise value");
        assertEquals(BigDecimal.ZERO.setScale(2), totals.taxable());
        assertEquals(BigDecimal.ZERO.setScale(2), totals.tax(), "no tax extracted from nothing");
        assertTrue(totals.total().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("an empty cart is free, not ₹49 of shipping on nothing")
    void emptyCartCostsNothing() {
        OrderTotals totals = OrderTotals.of(BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO.setScale(2), totals.shipping());
        assertEquals(BigDecimal.ZERO.setScale(2), totals.total());
    }

    @Test
    @DisplayName("the net amount rounds half-up at the paisa")
    void taxRoundsHalfUp() {
        // ₹100.05 / 1.18 is ₹84.78813…, which must settle at ₹84.79, leaving ₹15.26 of tax.
        // ₹49 of shipping applies because ₹100.05 is under the free-shipping threshold, and it is
        // added outside the tax extraction.
        OrderTotals totals = OrderTotals.of(new BigDecimal("100.05"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("84.79"), totals.taxable());
        assertEquals(new BigDecimal("15.26"), totals.tax());
        assertEquals(new BigDecimal("149.05"), totals.total(), "₹100.05 of goods plus ₹49 carriage");
        assertEquals(2, totals.total().scale(), "money is always two decimals");
    }

    @Test
    @DisplayName("a null subtotal or discount is zero, not a crash")
    void nullsAreZero() {
        OrderTotals totals = OrderTotals.of(null, null);
        assertEquals(BigDecimal.ZERO.setScale(2), totals.total());
    }
}
