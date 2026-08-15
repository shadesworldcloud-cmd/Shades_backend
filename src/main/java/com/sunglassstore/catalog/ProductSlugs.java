package com.sunglassstore.catalog;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Public product slugs: the storefront-visible identifier that replaced the sequential PRODUCT_ID
 * in /product/{...}.
 *
 * Pure and Spring-free so the rules can be unit-tested exhaustively without a context — the same
 * reason AutomaticOfferPricing is pure. Uniqueness is NOT decided here (that needs the database);
 * this class only produces candidates and judges whether a string is an acceptable slug.
 *
 * A slug is not a security control. It is harder to enumerate than 1,2,3… but every endpoint still
 * authorises independently — see the class comment on ProductController. The point of the change is
 * that a public URL should not publish how many products the shop has ever created.
 */
public final class ProductSlugs {

    /** Room for a long product name plus the suffix, and well under MySQL's index key limit. */
    public static final int MAX_LENGTH = 160;

    /**
     * Cap on the name-derived part. Long enough that no realistic product name is truncated mid-word
     * for cosmetic reasons, short enough that base + suffix can never exceed MAX_LENGTH.
     */
    static final int MAX_BASE_LENGTH = 100;

    /** Length of the disambiguating suffix, e.g. the "x7k9p2" in classic-aviator-sunglasses-x7k9p2. */
    static final int SUFFIX_LENGTH = 6;

    /**
     * No vowels, so a random suffix can never spell a word — including an offensive one — beside a
     * customer-visible product name. No 'l'/'1'/'0'/'o' either: these appear in URLs that people read
     * aloud and retype, and the pairs are indistinguishable in many fonts.
     */
    private static final char[] SUFFIX_ALPHABET = "bcdfghjkmnpqrstvwxyz23456789".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern VALID = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");

    /**
     * Words a slug may not take.
     *
     * Two separate hazards, deliberately in one list because an admin editing a slug should not have
     * to know which is which:
     *   - storefront route segments (/cart, /shop, /admin …). Today these cannot collide, because a
     *     product lives under the /product/ prefix. They are refused anyway so that moving products
     *     to the URL root later is a routing change and not a data migration.
     *   - the literal segments this API already serves under /api/products — "best-sellers",
     *     "search", "category", "admin", "slug". These are the ones that could actually bite, and
     *     only because a future refactor might resolve slugs directly at /api/products/{slug}.
     */
    private static final Set<String> RESERVED = Set.of(
            "admin", "api", "account", "best-sellers", "cart", "categories", "category",
            "collections", "checkout", "images", "index", "info", "my-orders", "new",
            "notifications", "null", "order", "orders", "product", "products", "search",
            "signin", "signup", "shop", "slug", "static", "undefined", "uploads", "variants",
            "wishlist");

    private ProductSlugs() {}

    /**
     * The name-derived part of a slug, or "" when the name yields nothing usable.
     *
     * "Ray-Ban Aviator® (Gold/Green)" -> "ray-ban-aviator-gold-green"
     * "Lunettes Élégantes"            -> "lunettes-elegantes"
     * "太陽眼鏡"                        -> ""   (caller must fall back to an opaque slug)
     *
     * NFKD then stripping combining marks folds accents onto their ASCII base rather than deleting
     * the letter: without it "Élégantes" becomes "l-gantes", which is worse than useless as a URL.
     * Scripts with no ASCII form legitimately reduce to empty — that is what generate() handles.
     */
    public static String toBaseSlug(String productName) {
        if (productName == null) return "";
        String decomposed = Normalizer.normalize(productName, Normalizer.Form.NFKD);
        String ascii = DIACRITICS.matcher(decomposed).replaceAll("");
        String hyphenated = NON_SLUG.matcher(ascii.toLowerCase(java.util.Locale.ROOT)).replaceAll("-");
        String trimmed = trimHyphens(hyphenated);
        if (trimmed.length() <= MAX_BASE_LENGTH) return trimmed;
        // Cut on a word boundary when there is one in the last quarter of the budget, so the
        // truncation reads as a shortened name rather than a corrupted one.
        String cut = trimmed.substring(0, MAX_BASE_LENGTH);
        int lastHyphen = cut.lastIndexOf('-');
        return trimHyphens(lastHyphen >= MAX_BASE_LENGTH * 3 / 4 ? cut.substring(0, lastHyphen) : cut);
    }

    /**
     * A candidate slug for a product name, with no uniqueness guarantee — the caller retries with
     * {@link #withFreshSuffix} until the database accepts one.
     *
     * Falls back to a purely opaque slug ("sw-<suffix>") when the name produces nothing, when the
     * result is reserved, or when it is all digits. The all-digits case is the subtle one: a product
     * genuinely named "2024" would otherwise slug to "2024", which /product/2024 cannot tell apart
     * from the legacy numeric id 2024. Suffixing it keeps the legacy-id redirect unambiguous.
     */
    public static String generate(String productName) {
        String base = toBaseSlug(productName);
        if (base.isEmpty() || RESERVED.contains(base) || DIGITS_ONLY.matcher(base).matches()) {
            return withFreshSuffix(base.isEmpty() ? "sw" : base);
        }
        return base;
    }

    /**
     * The same base with a new random suffix, replacing any suffix already present.
     *
     * Random rather than derived from the row: with a catalogue of a few thousand products, a
     * suffix computed from PRODUCT_ID is brute-forceable in milliseconds, which would re-publish
     * exactly the identifier this whole change removes.
     */
    public static String withFreshSuffix(String base) {
        String trimmed = stripSuffix(trimHyphens(base == null ? "" : base));
        String stem = trimmed.isEmpty() ? "sw" : trimmed;
        int budget = MAX_LENGTH - SUFFIX_LENGTH - 1;
        if (stem.length() > budget) stem = trimHyphens(stem.substring(0, budget));
        return stem + "-" + randomSuffix();
    }

    /**
     * Drops a trailing suffix this class produced, so the collision-retry loop re-rolls the suffix
     * instead of stacking one on the last rejected candidate — without it, each retry appended
     * another seven characters and the slug grew until it hit the column limit.
     *
     * A product genuinely ending in six consonant-and-digit characters would be misread as suffixed
     * and lose that token. Harmless by construction: the result is only ever fed back into
     * withFreshSuffix, so the row still gets a unique slug, just off a marginally shorter stem. The
     * alternative — making callers thread the original base through every retry — puts the same
     * invariant somewhere a future caller can quietly break.
     */
    private static String stripSuffix(String value) {
        int lastHyphen = value.lastIndexOf('-');
        if (lastHyphen < 1 || value.length() - lastHyphen - 1 != SUFFIX_LENGTH) return value;
        for (int index = lastHyphen + 1; index < value.length(); index++) {
            if (new String(SUFFIX_ALPHABET).indexOf(value.charAt(index)) < 0) return value;
        }
        return value.substring(0, lastHyphen);
    }

    static String randomSuffix() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int index = 0; index < SUFFIX_LENGTH; index++) {
            suffix.append(SUFFIX_ALPHABET[RANDOM.nextInt(SUFFIX_ALPHABET.length)]);
        }
        return suffix.toString();
    }

    /**
     * Whether a string is acceptable as a stored slug. Applied to admin-supplied slugs, which are
     * the only ones that do not come out of generate().
     */
    public static boolean isValid(String slug) {
        return slug != null
                && !slug.isEmpty()
                && slug.length() <= MAX_LENGTH
                && VALID.matcher(slug).matches()
                && !RESERVED.contains(slug)
                && !DIGITS_ONLY.matcher(slug).matches();
    }

    public static boolean isReserved(String slug) {
        return slug != null && RESERVED.contains(slug.toLowerCase(java.util.Locale.ROOT));
    }

    /** True when the path segment is a legacy numeric product id rather than a slug. */
    public static boolean isNumericId(String candidate) {
        return candidate != null && DIGITS_ONLY.matcher(candidate).matches();
    }

    private static String trimHyphens(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') start++;
        while (end > start && value.charAt(end - 1) == '-') end--;
        return value.substring(start, end);
    }
}
