package com.sunglassstore.offer;

import com.sunglassstore.entity.AutomaticOffer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Customer-facing wording generated from the offer's own numbers.
 *
 * Two separate strings, and the distinction is the point:
 *
 *   banner(offer) is what the top strip shows. An administrator may replace it with their own
 *   marketing copy, so it can say anything within its length limit.
 *
 *   terms(offer) is always generated and never customizable. The cart and checkout show it next to
 *   the discount, which is what stops a stale or vague custom banner from being the only statement
 *   of the offer the customer ever sees. If the administrator writes "Big savings this week!", the
 *   actual terms are still on the page where the money is.
 *
 * Nothing here escapes or renders HTML: these are plain strings, the API returns them as JSON text,
 * and the frontend interpolates them as text nodes. Administrator input is stripped of markup on
 * the way in (see AutomaticOfferServiceImpl.sanitiseDisplayText) rather than trusted here.
 */
public final class OfferMessages {

    private OfferMessages() {
    }

    /** Whole rupees when the amount is whole, otherwise two decimals — matches en-IN elsewhere. */
    public static String rupees(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal whole = value.setScale(0, RoundingMode.DOWN);
        boolean isWhole = value.compareTo(whole) == 0;
        return "₹" + group(isWhole ? whole.toPlainString() : value.toPlainString());
    }

    /** Indian digit grouping: 12,34,567.89 rather than 1,234,567.89. */
    private static String group(String plain) {
        String integerPart = plain;
        String fraction = "";
        int dot = plain.indexOf('.');
        if (dot >= 0) {
            integerPart = plain.substring(0, dot);
            fraction = plain.substring(dot);
        }
        if (integerPart.length() <= 3) {
            return integerPart + fraction;
        }
        String head = integerPart.substring(0, integerPart.length() - 3);
        String tail = integerPart.substring(integerPart.length() - 3);
        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int index = head.length() - 1; index >= 0; index--) {
            grouped.append(head.charAt(index));
            count++;
            if (count % 2 == 0 && index > 0) {
                grouped.append(',');
            }
        }
        return grouped.reverse() + "," + tail + fraction;
    }

    /**
     * Placeholder text an administrator types while trying the form out, which must never reach a
     * customer. Not an exhaustive blocklist — it cannot be — but these are the words that actually
     * turn up, and the length and single-token rules below catch the rest.
     */
    private static final java.util.Set<String> PLACEHOLDERS = java.util.Set.of(
            "message", "banner", "banner message", "text", "test", "testing", "todo", "tbd",
            "placeholder", "string", "offer", "offer message", "sample", "example", "n/a", "na",
            "none", "null", "undefined", "asdf", "xxx", "lorem ipsum", "dummy", "temp", "abc");

    /**
     * Whether an administrator-supplied banner message is fit to show a customer.
     *
     * This exists because of a real incident: an offer was saved with the banner message "message"
     * (filler, alongside the offer name "tttttt"), the storefront dutifully displayed it, and the
     * promo strip's uppercase styling rendered it as "MESSAGE" across the top of the shop. The value
     * travelled through the whole stack intact — nothing was mis-named or lost — so no amount of
     * contract-checking would have caught it. The only defence is refusing to present text that
     * cannot possibly be an offer statement.
     *
     * Three rules, all deliberately conservative, because wrongly rejecting real copy is the worse
     * failure — the fallback below is always accurate, so a rejection costs wording, not truth:
     *
     *   1. A single token with no whitespace is a label or a field name, not a sentence.
     *   2. Under 12 characters cannot state a quantity and an amount.
     *   3. A known placeholder word, case-insensitive.
     *
     * A rejected message is not deleted: it stays in the row, the administrator screen shows the
     * generated wording as `effectiveBannerMessage` so they can see what customers get, and fixing
     * the text is all it takes to have their own wording appear.
     */
    public static boolean isUsableBannerMessage(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim().replaceAll("\\s{2,}", " ");
        if (trimmed.isEmpty() || trimmed.length() < 12) {
            return false;
        }
        if (!trimmed.contains(" ")) {
            return false;
        }
        return !PLACEHOLDERS.contains(trimmed.toLowerCase(java.util.Locale.ROOT));
    }

    public static String banner(AutomaticOffer offer) {
        if (isUsableBannerMessage(offer.getBannerMessage())) {
            return offer.getBannerMessage().trim().replaceAll("\\s{2,}", " ");
        }
        return defaultBanner(offer);
    }

    public static String defaultBanner(AutomaticOffer offer) {
        int required = offer.getRequiredQuantity() == null ? 0 : offer.getRequiredQuantity();
        String amount = rupees(offer.getDiscountPerGroup());
        return "Buy any " + required + " eligible products and get " + amount
                + " off automatically for every complete " + groupNoun(required) + ".";
    }

    /** The non-negotiable statement of what the offer actually does. */
    public static String terms(AutomaticOffer offer) {
        int required = offer.getRequiredQuantity() == null ? 0 : offer.getRequiredQuantity();
        String amount = rupees(offer.getDiscountPerGroup());
        StringBuilder terms = new StringBuilder(amount).append(" off every ").append(required)
                .append(" eligible units. Unmatched units are not discounted.");
        if (offer.getMinimumOrderSubtotal() != null
                && offer.getMinimumOrderSubtotal().compareTo(BigDecimal.ZERO) > 0) {
            terms.append(" Minimum eligible subtotal ")
                    .append(rupees(offer.getMinimumOrderSubtotal())).append('.');
        }
        return terms.toString();
    }

    private static String groupNoun(int requiredQuantity) {
        return requiredQuantity == 2 ? "pair" : "group of " + requiredQuantity;
    }
}
