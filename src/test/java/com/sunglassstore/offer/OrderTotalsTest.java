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
 */
class OrderTotalsTest {

    @Test
    @DisplayName("tax is charged on the discounted amount, not the list price")
    void taxFollowsTheDiscount() {
        OrderTotals totals = OrderTotals.of(new BigDecimal("5000.00"), new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("4000.00"), totals.taxable());
        assertEquals(new BigDecimal("720.00"), totals.tax());
        assertEquals(new BigDecimal("4720.00"), totals.total());
        assertEquals(BigDecimal.ZERO.setScale(2), totals.shipping(), "free above ₹500");
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
        assertEquals(BigDecimal.ZERO.setScale(2), totals.tax(), "no tax on nothing");
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
    @DisplayName("tax rounds half-up at the paisa")
    void taxRoundsHalfUp() {
        // 18% of ₹100.05 is ₹18.009, which must settle at ₹18.01.
        OrderTotals totals = OrderTotals.of(new BigDecimal("100.05"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("18.01"), totals.tax());
        assertEquals(2, totals.total().scale(), "money is always two decimals");
    }

    @Test
    @DisplayName("a null subtotal or discount is zero, not a crash")
    void nullsAreZero() {
        OrderTotals totals = OrderTotals.of(null, null);
        assertEquals(BigDecimal.ZERO.setScale(2), totals.total());
    }
}
