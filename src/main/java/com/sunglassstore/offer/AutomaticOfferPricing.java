package com.sunglassstore.offer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The automatic quantity offer calculation, and the only place it is expressed.
 *
 * Deliberately pure: no repositories, no clock, no Spring. Everything it needs is in the arguments,
 * which is what lets the whole quantity matrix be tested without a database and guarantees the cart
 * quote, the checkout quote and order creation cannot drift apart — they all call this.
 *
 * The rule:
 *   eligibleQuantity = sum of eligible purchasable UNITS (not lines)
 *   completeGroups   = floor(eligibleQuantity / requiredQuantity)
 *   rawDiscount      = completeGroups * discountPerGroup
 *   discount         = min(rawDiscount, eligibleMerchandiseSubtotal)
 *
 * Counting units rather than lines is the whole point of the aggregation below: one line of seven
 * and seven lines of one produce the same three groups, so splitting or merging a cart line can
 * never change what the customer pays.
 *
 * Arithmetic runs in integer paise. BigDecimal alone would still be exact for the multiplication,
 * but the proportional allocation needs a smallest currency unit to distribute remainders in, and
 * doing that in paise is what makes the per-line shares add up to the order-level discount exactly
 * rather than nearly.
 */
public final class AutomaticOfferPricing {

    private AutomaticOfferPricing() {
    }

    /** One cart or order line as the calculation sees it. */
    public record Line(Long variantId, int quantity, BigDecimal unitPrice, boolean eligible) {
    }

    /**
     * @param eligibleQuantity  units that counted toward groups
     * @param completeGroups    floor(eligibleQuantity / requiredQuantity)
     * @param discount          what comes off the merchandise subtotal
     * @param eligibleSubtotal  merchandise value of the eligible units, the cap on the discount
     * @param unitsToNextGroup  how many more eligible units would earn another group
     * @param lineDiscounts     per-line share of {@code discount}, keyed by variant id; sums exactly
     */
    public record Result(int eligibleQuantity,
                         int completeGroups,
                         BigDecimal discount,
                         BigDecimal eligibleSubtotal,
                         int unitsToNextGroup,
                         Map<Long, BigDecimal> lineDiscounts) {

        public boolean applies() {
            return discount.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    public static final Result NONE = new Result(0, 0, BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2), 0, Map.of());

    /**
     * @param requiredQuantity group size, at least 2 (a smaller value disables the offer rather
     *                         than dividing by it — a misconfiguration must not become free money)
     * @param discountPerGroup amount off per complete group, must be positive
     * @param minimumSubtotal  optional minimum eligible merchandise subtotal, null or zero for none
     */
    public static Result calculate(List<Line> lines,
                                   int requiredQuantity,
                                   BigDecimal discountPerGroup,
                                   BigDecimal minimumSubtotal) {
        if (lines == null || lines.isEmpty() || requiredQuantity < 2
                || discountPerGroup == null || discountPerGroup.compareTo(BigDecimal.ZERO) <= 0) {
            return NONE;
        }

        long eligibleUnits = 0;
        long eligibleSubtotalPaise = 0;
        List<Line> eligibleLines = new ArrayList<>();
        for (Line line : lines) {
            // A non-positive quantity or a missing/negative price is not a purchasable unit. Such a
            // line is skipped rather than rejected: the offer is a bonus, and refusing to price a
            // cart because of one unusable line would block checkout instead of the line.
            if (!line.eligible() || line.quantity() <= 0 || line.unitPrice() == null
                    || line.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            eligibleUnits += line.quantity();
            eligibleSubtotalPaise += toPaise(line.unitPrice()) * line.quantity();
            eligibleLines.add(line);
        }

        BigDecimal eligibleSubtotal = fromPaise(eligibleSubtotalPaise);
        if (eligibleUnits == 0 || eligibleSubtotalPaise == 0) {
            return NONE;
        }
        if (minimumSubtotal != null && eligibleSubtotal.compareTo(minimumSubtotal) < 0) {
            // Reported with the real eligible figures so the caller can still show progress toward
            // the minimum; only the discount is withheld.
            return new Result((int) eligibleUnits, 0, BigDecimal.ZERO.setScale(2),
                    eligibleSubtotal, unitsToNextGroup(eligibleUnits, requiredQuantity), Map.of());
        }

        long completeGroups = eligibleUnits / requiredQuantity;
        long rawDiscountPaise = completeGroups * toPaise(discountPerGroup);
        // The cap is the eligible merchandise value, never the whole subtotal: an offer must not be
        // funded by items it does not cover, and the order total must never go negative.
        long discountPaise = Math.min(rawDiscountPaise, eligibleSubtotalPaise);

        return new Result(
                (int) eligibleUnits,
                (int) completeGroups,
                fromPaise(discountPaise),
                eligibleSubtotal,
                unitsToNextGroup(eligibleUnits, requiredQuantity),
                allocate(eligibleLines, discountPaise));
    }

    /**
     * Spreads an already-decided discount across lines, treating every supplied line as sharing it.
     *
     * Exposed for the coupon path: a coupon discounts the whole cart, so its per-line shares are the
     * same largest-remainder allocation with no eligibility filter. Both promotions therefore write
     * ORDER_ITEMS.DISCOUNT_AMOUNT the same way, and refunds have one rule to read rather than two.
     */
    public static Map<Long, BigDecimal> allocateAcross(List<Line> lines, BigDecimal discount) {
        if (lines == null || lines.isEmpty() || discount == null
                || discount.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of();
        }
        List<Line> usable = new ArrayList<>();
        for (Line line : lines) {
            if (line.quantity() > 0 && line.unitPrice() != null
                    && line.unitPrice().compareTo(BigDecimal.ZERO) > 0) {
                usable.add(line);
            }
        }
        return allocate(usable, toPaise(discount));
    }

    private static int unitsToNextGroup(long eligibleUnits, int requiredQuantity) {
        long remainder = eligibleUnits % requiredQuantity;
        return (int) (requiredQuantity - remainder);
    }

    /**
     * Splits the order-level discount across the eligible lines in proportion to their value.
     *
     * Largest-remainder, not per-line rounding. Rounding each line independently leaves the shares
     * short or over by a few paise, and a total that does not match the amount charged is the kind
     * of discrepancy that only shows up in a refund months later. Here every line takes its floor
     * share, and the paise left over go one each to the lines with the largest dropped fractions —
     * so the shares always add up to exactly the discount.
     *
     * Ties break on variant id, and the input is sorted before allocating, so the same cart always
     * produces the same allocation regardless of the order the lines arrived in.
     *
     * Each line's share is also capped at its own value, which matters when the discount equals the
     * eligible subtotal: no line can be pushed below zero.
     */
    private static Map<Long, BigDecimal> allocate(List<Line> eligibleLines, long discountPaise) {
        if (discountPaise <= 0 || eligibleLines.isEmpty()) {
            return Map.of();
        }
        List<Line> ordered = new ArrayList<>(eligibleLines);
        ordered.sort(Comparator.comparing(line -> line.variantId() == null ? Long.MAX_VALUE : line.variantId()));

        long totalValuePaise = 0;
        for (Line line : ordered) {
            totalValuePaise += toPaise(line.unitPrice()) * line.quantity();
        }

        long[] share = new long[ordered.size()];
        // Remainders are compared, never summed, so they only need to be ordered — but they are the
        // modulus of a product that does not fit in a long, so they are kept as BigInteger too.
        java.math.BigInteger[] remainder = new java.math.BigInteger[ordered.size()];
        long assigned = 0;
        java.math.BigInteger total = java.math.BigInteger.valueOf(totalValuePaise);
        java.math.BigInteger discount = java.math.BigInteger.valueOf(discountPaise);
        for (int index = 0; index < ordered.size(); index++) {
            Line line = ordered.get(index);
            long lineValue = toPaise(line.unitPrice()) * line.quantity();
            // lineValue * discountPaise overflows a long for large carts — 10,000 units of a
            // ₹99,999.99 product against a ₹25,00,000 discount is 2.5e19 against a 9.2e18 ceiling,
            // and the wrap-around produced a plausible-looking wrong discount rather than an error.
            // The quotient always fits (it cannot exceed the line's own value); only the
            // intermediate product does not, so the product is the part that gets a wider type.
            java.math.BigInteger[] divided = java.math.BigInteger.valueOf(lineValue)
                    .multiply(discount).divideAndRemainder(total);
            share[index] = divided[0].longValueExact();
            remainder[index] = divided[1];
            assigned += share[index];
        }

        // Hand out the leftover paise, largest dropped fraction first. At most one extra paisa per
        // line, and never more than the line's own value.
        long leftover = discountPaise - assigned;
        Integer[] byRemainder = new Integer[ordered.size()];
        for (int index = 0; index < byRemainder.length; index++) {
            byRemainder[index] = index;
        }
        java.util.Arrays.sort(byRemainder, (left, right) -> {
            int compared = remainder[right].compareTo(remainder[left]);
            return compared != 0 ? compared : Integer.compare(left, right);
        });
        for (int position = 0; leftover > 0 && position < byRemainder.length; position++) {
            int index = byRemainder[position];
            Line line = ordered.get(index);
            long lineValue = toPaise(line.unitPrice()) * line.quantity();
            if (share[index] < lineValue) {
                share[index] += 1;
                leftover -= 1;
            }
        }

        Map<Long, BigDecimal> allocation = new LinkedHashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            if (share[index] > 0) {
                allocation.merge(ordered.get(index).variantId(), fromPaise(share[index]), BigDecimal::add);
            }
        }
        return allocation;
    }

    /** Exact because every money value in this schema is DECIMAL(12,2); scaling can never round. */
    private static long toPaise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    private static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }
}
