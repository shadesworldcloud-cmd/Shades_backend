package com.sunglassstore.offer;

import java.math.BigDecimal;

/**
 * The one place that decides which merchandise promotion a cart gets.
 *
 * There was no stacking policy before this: a coupon code was the only merchandise discount, so
 * "which one applies" never came up. It does now, and the answer has to be centralized — a rule
 * spread across the cart quote and order creation is a rule that eventually disagrees with itself,
 * and the customer sees one discount in the bag and a different one on the invoice.
 *
 * Policy, the safe default from the brief because no prior product rule existed:
 *
 *   1. The automatic quantity offer and a merchandise coupon do NOT stack.
 *   2. Both are calculated in full, and the single larger valid discount is applied.
 *   3. A tie goes to the automatic offer, because the customer did nothing to earn the coupon's
 *      place and an offer that needs no code cannot be "lost" by the customer.
 *   4. Shipping promotions are untouched — they only affect shipping, which is outside this
 *      decision entirely.
 *
 * Request ordering cannot change the result: both inputs are computed before the comparison, and
 * the comparison is a pure function of the two amounts. There is no "first one wins" path.
 */
public final class MerchandisePromotionPolicy {

    private MerchandisePromotionPolicy() {
    }

    public enum AppliedPromotion { NONE, AUTOMATIC_OFFER, COUPON }

    /**
     * @param applied         which promotion the customer is charged under
     * @param discount        the merchandise discount to apply, never negative
     * @param label           short human-readable name of what was applied
     * @param suppressedLabel the promotion that was calculated but not applied, or null
     * @param reason          why the suppressed promotion was not applied, or null. Shown to the
     *                        customer: "we did not silently pick one" is the point.
     */
    public record Decision(AppliedPromotion applied,
                           BigDecimal discount,
                           String label,
                           String suppressedLabel,
                           String reason) {
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public static Decision decide(BigDecimal automaticDiscount, String automaticLabel,
                                  BigDecimal couponDiscount, String couponLabel) {
        BigDecimal automatic = normalise(automaticDiscount);
        BigDecimal coupon = normalise(couponDiscount);

        boolean hasAutomatic = automatic.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCoupon = coupon.compareTo(BigDecimal.ZERO) > 0;

        if (!hasAutomatic && !hasCoupon) {
            return new Decision(AppliedPromotion.NONE, ZERO, null, null, null);
        }
        if (hasAutomatic && !hasCoupon) {
            return new Decision(AppliedPromotion.AUTOMATIC_OFFER, automatic, automaticLabel, null, null);
        }
        if (!hasAutomatic && hasCoupon) {
            return new Decision(AppliedPromotion.COUPON, coupon, couponLabel, null, null);
        }

        // Both qualify. Ties included, the automatic offer wins — see rule 3 above.
        if (automatic.compareTo(coupon) >= 0) {
            return new Decision(AppliedPromotion.AUTOMATIC_OFFER, automatic, automaticLabel, couponLabel,
                    "Your automatic offer saves more, so " + describe(couponLabel)
                            + " was not applied. These cannot be combined.");
        }
        return new Decision(AppliedPromotion.COUPON, coupon, couponLabel, automaticLabel,
                describe(couponLabel) + " saves more, so " + describe(automaticLabel)
                        + " was not applied. These cannot be combined.");
    }

    private static String describe(String label) {
        return label == null || label.isBlank() ? "the other offer" : label;
    }

    private static BigDecimal normalise(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
