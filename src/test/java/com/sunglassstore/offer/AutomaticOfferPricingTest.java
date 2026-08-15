package com.sunglassstore.offer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The offer arithmetic, tested where it is cheap to test exhaustively.
 *
 * The E2E suite proves the same numbers through a real cart, checkout and order, but one HTTP
 * round trip per quantity would be minutes for what runs here in milliseconds — so the quantity
 * matrix, the rounding behaviour and the split-invariance property live at this level, and E2E
 * proves the wiring rather than the maths.
 *
 * Every amount is a constructor argument. Nothing below assumes ₹500 or a group of two beyond the
 * cases that exist specifically to pin the brief's worked example.
 */
class AutomaticOfferPricingTest {

    private static final BigDecimal FIVE_HUNDRED = new BigDecimal("500.00");
    private static final BigDecimal UNIT_PRICE = new BigDecimal("1000.00");

    private static AutomaticOfferPricing.Line eligible(long variantId, int quantity, String price) {
        return new AutomaticOfferPricing.Line(variantId, quantity, new BigDecimal(price), true);
    }

    private static AutomaticOfferPricing.Line ineligible(long variantId, int quantity, String price) {
        return new AutomaticOfferPricing.Line(variantId, quantity, new BigDecimal(price), false);
    }

    private static AutomaticOfferPricing.Result oneLineOf(int quantity) {
        return AutomaticOfferPricing.calculate(
                quantity == 0 ? List.of() : List.of(eligible(1L, quantity, "1000.00")),
                2, FIVE_HUNDRED, null);
    }

    // ── The mandated matrix ──────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} units -> {1} groups -> ₹{2}")
    @CsvSource({
            "0,  0, 0.00",
            "1,  0, 0.00",
            "2,  1, 500.00",
            "3,  1, 500.00",
            "4,  2, 1000.00",
            "7,  3, 1500.00",
            "10, 5, 2500.00",
    })
    @DisplayName("a 2-for-₹500 offer discounts one complete group at a time")
    void quantityMatrix(int units, int expectedGroups, String expectedDiscount) {
        AutomaticOfferPricing.Result result = oneLineOf(units);

        assertEquals(expectedGroups, result.completeGroups(), units + " units");
        assertEquals(new BigDecimal(expectedDiscount), result.discount(), units + " units");
    }

    @Test
    @DisplayName("the brief's worked example: 7 units, 3 pairs, ₹1,500, one unit unmatched")
    void workedExample() {
        AutomaticOfferPricing.Result result = oneLineOf(7);

        assertEquals(7, result.eligibleQuantity());
        assertEquals(3, result.completeGroups());
        assertEquals(new BigDecimal("1500.00"), result.discount());
        // The eighth unit is what the next group needs; the seventh earns nothing on its own.
        assertEquals(1, result.unitsToNextGroup());
    }

    // ── Units, not lines ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("one line of seven and seven lines of one produce the same discount")
    void lineSplittingCannotChangeTheDiscount() {
        AutomaticOfferPricing.Result single = oneLineOf(7);

        List<AutomaticOfferPricing.Line> sevenLines = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            sevenLines.add(eligible(100L + index, 1, "1000.00"));
        }
        AutomaticOfferPricing.Result split =
                AutomaticOfferPricing.calculate(sevenLines, 2, FIVE_HUNDRED, null);

        assertEquals(single.completeGroups(), split.completeGroups());
        assertEquals(single.discount(), split.discount());
        assertEquals(single.eligibleQuantity(), split.eligibleQuantity());
    }

    @Test
    @DisplayName("merging two lines of the same total quantity leaves the discount unchanged")
    void lineMergingCannotChangeTheDiscount() {
        AutomaticOfferPricing.Result merged =
                AutomaticOfferPricing.calculate(List.of(eligible(1L, 6, "1000.00")), 2, FIVE_HUNDRED, null);
        AutomaticOfferPricing.Result apart = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 4, "1000.00"), eligible(2L, 2, "1000.00")), 2, FIVE_HUNDRED, null);

        assertEquals(merged.discount(), apart.discount());
    }

    @Test
    @DisplayName("different variants of the same product aggregate into shared groups")
    void variantsOfOneProductAggregate() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(11L, 1, "1000.00"), eligible(12L, 1, "1000.00")), 2, FIVE_HUNDRED, null);

        assertEquals(1, result.completeGroups(), "two single units still make one pair");
        assertEquals(new BigDecimal("500.00"), result.discount());
    }

    @Test
    @DisplayName("the order lines arrive in cannot change the result")
    void allocationIsIndependentOfInputOrder() {
        List<AutomaticOfferPricing.Line> lines = new ArrayList<>(List.of(
                eligible(5L, 3, "1299.00"), eligible(2L, 2, "749.50"), eligible(9L, 2, "2100.00")));
        AutomaticOfferPricing.Result first = AutomaticOfferPricing.calculate(lines, 2, FIVE_HUNDRED, null);
        Collections.reverse(lines);
        AutomaticOfferPricing.Result reversed = AutomaticOfferPricing.calculate(lines, 2, FIVE_HUNDRED, null);

        assertEquals(first.discount(), reversed.discount());
        assertEquals(first.lineDiscounts(), reversed.lineDiscounts());
    }

    // ── Scope ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ineligible units neither earn groups nor receive any allocation")
    void ineligibleUnitsAreExcludedEntirely() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 3, "1000.00"), ineligible(2L, 5, "1000.00")),
                2, FIVE_HUNDRED, null);

        assertEquals(3, result.eligibleQuantity(), "only the eligible line counts");
        assertEquals(1, result.completeGroups());
        assertEquals(new BigDecimal("500.00"), result.discount());
        assertFalse(result.lineDiscounts().containsKey(2L), "an ineligible line carries no discount");
    }

    @Test
    @DisplayName("a zero or negative quantity is not a purchasable unit")
    void nonPurchasableQuantitiesAreIgnored() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 2, "1000.00"), eligible(2L, 0, "1000.00"), eligible(3L, -4, "1000.00")),
                2, FIVE_HUNDRED, null);

        assertEquals(2, result.eligibleQuantity());
        assertEquals(new BigDecimal("500.00"), result.discount());
    }

    @Test
    @DisplayName("a free item cannot earn a group")
    void zeroPricedUnitsAreIgnored() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 1, "1000.00"), eligible(2L, 3, "0.00")), 2, FIVE_HUNDRED, null);

        assertEquals(1, result.eligibleQuantity(), "the free units do not count");
        assertEquals(0, result.completeGroups());
        assertEquals(BigDecimal.ZERO.setScale(2), result.discount());
    }

    // ── The cap ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the discount never exceeds the eligible merchandise subtotal")
    void discountIsCappedAtEligibleValue() {
        // Four units at ₹100 = ₹400 of merchandise, but two groups would be ₹1,000.
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 4, "100.00")), 2, FIVE_HUNDRED, null);

        assertEquals(new BigDecimal("400.00"), result.discount(), "capped at the eligible subtotal");
        assertEquals(new BigDecimal("400.00"), result.eligibleSubtotal());
    }

    @Test
    @DisplayName("the cap is the eligible subtotal, not the whole cart")
    void capIgnoresIneligibleValue() {
        // ₹400 eligible + ₹9,000 ineligible. The offer must not be funded by items it does not cover.
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 4, "100.00"), ineligible(2L, 3, "3000.00")),
                2, FIVE_HUNDRED, null);

        assertEquals(new BigDecimal("400.00"), result.discount());
    }

    @Test
    @DisplayName("a capped discount still allocates exactly, with no line pushed below zero")
    void cappedAllocationStaysWithinEachLine() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 2, "100.00"), eligible(2L, 2, "100.00")), 2, FIVE_HUNDRED, null);

        assertEquals(new BigDecimal("400.00"), result.discount());
        assertEquals(new BigDecimal("200.00"), result.lineDiscounts().get(1L));
        assertEquals(new BigDecimal("200.00"), result.lineDiscounts().get(2L));
        assertEquals(result.discount(), sum(result.lineDiscounts()));
    }

    // ── Allocation and rounding ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("per-line shares add up to the order discount exactly, to the paisa")
    void allocationSumsToTheDiscountExactly() {
        // Three differently priced lines whose proportional shares do not divide evenly: ₹1,500
        // across values of ₹3,897, ₹1,499 and ₹4,200.
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 3, "1299.00"), eligible(2L, 1, "1499.00"), eligible(3L, 2, "2100.00")),
                2, FIVE_HUNDRED, null);

        assertEquals(6, result.eligibleQuantity());
        assertEquals(3, result.completeGroups());
        assertEquals(new BigDecimal("1500.00"), result.discount());
        assertEquals(result.discount(), sum(result.lineDiscounts()),
                "largest-remainder allocation must not lose or invent paise");
    }

    @Test
    @DisplayName("an indivisible three-way split still reconciles")
    void indivisibleSplitReconciles() {
        // Three equal lines sharing ₹1,000.00: 33,333.33 paise each leaves one paisa over.
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 2, "1000.00"), eligible(2L, 2, "1000.00"), eligible(3L, 2, "1000.00")),
                2, new BigDecimal("333.34"), null);

        assertEquals(3, result.completeGroups());
        assertEquals(new BigDecimal("1000.02"), result.discount());
        assertEquals(result.discount(), sum(result.lineDiscounts()));
    }

    @Test
    @DisplayName("prices with paise allocate without drift")
    void paisePricesAllocateExactly() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 1, "749.99"), eligible(2L, 1, "1250.01"), eligible(3L, 2, "333.33")),
                2, new BigDecimal("100.01"), null);

        assertEquals(4, result.eligibleQuantity());
        assertEquals(2, result.completeGroups());
        assertEquals(new BigDecimal("200.02"), result.discount());
        assertEquals(result.discount(), sum(result.lineDiscounts()));
    }

    @Test
    @DisplayName("no line is allocated more than its own value")
    void noLineExceedsItsOwnValue() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 1, "10.00"), eligible(2L, 1, "5000.00")), 2, FIVE_HUNDRED, null);

        assertTrue(result.lineDiscounts().getOrDefault(1L, BigDecimal.ZERO)
                        .compareTo(new BigDecimal("10.00")) <= 0,
                "a ₹10 line cannot absorb more than ₹10 of discount");
        assertEquals(result.discount(), sum(result.lineDiscounts()));
    }

    // ── Configuration guards ────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "group size {0} disables the offer")
    @CsvSource({"1", "0", "-2"})
    @DisplayName("a group size below two disables the offer instead of dividing by it")
    void groupSizeBelowTwoDisablesTheOffer(int requiredQuantity) {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 10, "1000.00")), requiredQuantity, FIVE_HUNDRED, null);

        assertEquals(BigDecimal.ZERO.setScale(2), result.discount());
        assertFalse(result.applies());
    }

    @Test
    @DisplayName("a non-positive discount per group yields no discount")
    void nonPositiveDiscountYieldsNothing() {
        assertFalse(AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 4, "1000.00")), 2, BigDecimal.ZERO, null).applies());
        assertFalse(AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 4, "1000.00")), 2, new BigDecimal("-500.00"), null).applies());
    }

    @Test
    @DisplayName("an empty cart earns nothing")
    void emptyCartEarnsNothing() {
        assertFalse(AutomaticOfferPricing.calculate(List.of(), 2, FIVE_HUNDRED, null).applies());
        assertFalse(AutomaticOfferPricing.calculate(null, 2, FIVE_HUNDRED, null).applies());
    }

    // ── Minimum subtotal ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("below the minimum subtotal the discount is withheld but progress is still reported")
    void minimumSubtotalWithholdsTheDiscount() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 2, "100.00")), 2, FIVE_HUNDRED, new BigDecimal("1000.00"));

        assertEquals(BigDecimal.ZERO.setScale(2), result.discount());
        assertEquals(2, result.eligibleQuantity(), "the units are still counted for the shopper's benefit");
        assertTrue(result.lineDiscounts().isEmpty());
    }

    @Test
    @DisplayName("exactly at the minimum subtotal the offer applies")
    void minimumSubtotalIsInclusive() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 2, "500.00")), 2, FIVE_HUNDRED, new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("500.00"), result.discount());
    }

    // ── Larger group sizes ──────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} units in groups of 3 -> {1} groups")
    @CsvSource({"2, 0", "3, 1", "5, 1", "6, 2", "10, 3"})
    @DisplayName("nothing is special about a group of two")
    void groupSizeIsConfigurable(int units, int expectedGroups) {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, units, "1000.00")), 3, new BigDecimal("250.00"), null);

        assertEquals(expectedGroups, result.completeGroups());
        assertEquals(new BigDecimal("250.00").multiply(BigDecimal.valueOf(expectedGroups)).setScale(2),
                result.discount());
    }

    @Test
    @DisplayName("units to the next group counts up to the group size, never to zero")
    void unitsToNextGroupIsAlwaysPositive() {
        assertEquals(1, AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 1, "1000.00")), 2, FIVE_HUNDRED, null).unitsToNextGroup());
        // A cart already on a group boundary needs a whole new group, not zero more units.
        assertEquals(2, AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 2, "1000.00")), 2, FIVE_HUNDRED, null).unitsToNextGroup());
        assertEquals(2, AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 4, "1000.00")), 3, FIVE_HUNDRED, null).unitsToNextGroup());
    }

    @Test
    @DisplayName("a large cart does not overflow the paise arithmetic")
    void largeCartStaysExact() {
        AutomaticOfferPricing.Result result = AutomaticOfferPricing.calculate(
                List.of(eligible(1L, 10_000, "99999.99")), 2, new BigDecimal("500.00"), null);

        assertEquals(5_000, result.completeGroups());
        assertEquals(new BigDecimal("2500000.00"), result.discount());
        assertEquals(result.discount(), sum(result.lineDiscounts()));
    }

    private static BigDecimal sum(Map<Long, BigDecimal> allocation) {
        return allocation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }
}
