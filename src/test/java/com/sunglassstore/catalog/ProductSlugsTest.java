package com.sunglassstore.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slug generation, tested exhaustively here because every case is a pure string transform.
 *
 * The E2E suite proves that a slug reaches the URL bar and that the legacy numeric id redirects
 * onto it; the character-level rules — accents, punctuation, scripts with no ASCII form, the
 * all-digits trap — belong at this level where one case costs microseconds instead of a page load.
 */
class ProductSlugsTest {

    @ParameterizedTest
    @DisplayName("names normalise to lowercase hyphenated ASCII")
    @CsvSource({
            "'Classic Aviator Sunglasses', classic-aviator-sunglasses",
            "'  Leading and trailing  ', leading-and-trailing",
            "'MiXeD CaSe', mixed-case",
            "'Ray-Ban Aviator', ray-ban-aviator",
            "'Ray-Ban  Aviator®  (Gold/Green)', ray-ban-aviator-gold-green",
            "'Multiple---hyphens', multiple-hyphens",
            "'Trailing punctuation!!!', trailing-punctuation",
            "'50% off Wayfarer', 50-off-wayfarer",
            "'Café Élégante', cafe-elegante",
            "'Lunettes Élégantes', lunettes-elegantes",
    })
    void normalisesNames(String name, String expected) {
        assertEquals(expected, ProductSlugs.toBaseSlug(name));
    }

    @ParameterizedTest
    @DisplayName("names with no ASCII form produce an empty base rather than mangled letters")
    @ValueSource(strings = {"太陽眼鏡", "!!!", "---", "   ", "😎😎"})
    void unusableNamesProduceEmptyBase(String name) {
        assertEquals("", ProductSlugs.toBaseSlug(name));
    }

    @Test
    @DisplayName("a null name is an empty base, not an exception")
    void nullName() {
        assertEquals("", ProductSlugs.toBaseSlug(null));
    }

    @Test
    @DisplayName("an unusable name still yields a valid opaque slug")
    void unusableNameFallsBackToOpaqueSlug() {
        String slug = ProductSlugs.generate("太陽眼鏡");
        assertTrue(slug.startsWith("sw-"), slug);
        assertTrue(ProductSlugs.isValid(slug), slug);
    }

    @Test
    @DisplayName("a long name is cut on a word boundary and stays within the column")
    void longNameIsTruncatedOnAWordBoundary() {
        String name = "Premium Polarised Titanium Aviator Sunglasses With Gradient Lenses And Spring Hinges For Everyday Wear";
        String base = ProductSlugs.toBaseSlug(name);
        assertTrue(base.length() <= ProductSlugs.MAX_BASE_LENGTH, "length " + base.length());
        assertFalse(base.endsWith("-"), base);
        // Cut on a boundary, so the last token is a whole word rather than a fragment.
        assertTrue(name.toLowerCase().contains(base.substring(base.lastIndexOf('-') + 1)), base);
    }

    @Test
    @DisplayName("a name longer than the whole column still produces a slug that fits with a suffix")
    void veryLongNameWithSuffixFitsTheColumn() {
        String slug = ProductSlugs.withFreshSuffix(ProductSlugs.toBaseSlug("a".repeat(400)));
        assertTrue(slug.length() <= ProductSlugs.MAX_LENGTH, "length " + slug.length());
        assertTrue(ProductSlugs.isValid(slug), slug);
    }

    // ── The all-digits trap ───────────────────────────────────────────────────────────────
    // A product named "2024" must not slug to "2024", or /product/2024 becomes ambiguous with the
    // legacy numeric id 2024 and the redirect cannot decide which it is looking at.

    @ParameterizedTest
    @DisplayName("an all-digit name is suffixed so it can never look like a legacy id")
    @ValueSource(strings = {"2024", "50", "007"})
    void allDigitNamesAreSuffixed(String name) {
        String slug = ProductSlugs.generate(name);
        assertNotEquals(name, slug);
        assertTrue(slug.startsWith(name + "-"), slug);
        assertFalse(ProductSlugs.isNumericId(slug), slug);
        assertTrue(ProductSlugs.isValid(slug), slug);
    }

    @Test
    @DisplayName("an all-digit slug is refused even if an admin types it directly")
    void allDigitSlugIsInvalid() {
        assertFalse(ProductSlugs.isValid("2024"));
    }

    // ── Reserved words ────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @DisplayName("reserved route and API words are refused")
    @ValueSource(strings = {"admin", "cart", "shop", "search", "best-sellers", "products", "new", "uploads"})
    void reservedWordsAreRefused(String word) {
        assertFalse(ProductSlugs.isValid(word), word);
        assertTrue(ProductSlugs.isReserved(word), word);
        // …and a product actually named that still gets a usable slug, by suffixing.
        String slug = ProductSlugs.generate(word);
        assertTrue(ProductSlugs.isValid(slug), slug);
        assertTrue(slug.startsWith(word + "-"), slug);
    }

    @Test
    @DisplayName("reserved matching is case-insensitive")
    void reservedIsCaseInsensitive() {
        assertTrue(ProductSlugs.isReserved("ADMIN"));
    }

    @Test
    @DisplayName("a reserved word inside a longer name is fine")
    void reservedWordAsPartOfALongerNameIsAllowed() {
        assertEquals("admin-favourite-aviator", ProductSlugs.generate("Admin Favourite Aviator"));
    }

    // ── Validation of admin-supplied slugs ────────────────────────────────────────────────

    @ParameterizedTest
    @DisplayName("malformed slugs are refused")
    @ValueSource(strings = {
            "Has Spaces", "UPPERCASE", "trailing-", "-leading", "double--hyphen",
            "under_score", "punctuation!", "sl/ash", "dot.dot", "café", "", " ",
    })
    void malformedSlugsAreRefused(String slug) {
        assertFalse(ProductSlugs.isValid(slug), slug);
    }

    @Test
    @DisplayName("a null slug is invalid rather than an exception")
    void nullSlugIsInvalid() {
        assertFalse(ProductSlugs.isValid(null));
    }

    @Test
    @DisplayName("a slug one character over the limit is refused")
    void overlongSlugIsRefused() {
        assertFalse(ProductSlugs.isValid("a".repeat(ProductSlugs.MAX_LENGTH + 1)));
        assertTrue(ProductSlugs.isValid("a".repeat(ProductSlugs.MAX_LENGTH)));
    }

    @ParameterizedTest
    @DisplayName("well-formed slugs are accepted")
    @ValueSource(strings = {"classic-aviator", "a", "a1", "classic-aviator-x7k9p2", "50-off-wayfarer"})
    void wellFormedSlugsAreAccepted(String slug) {
        assertTrue(ProductSlugs.isValid(slug), slug);
    }

    // ── Suffixes ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every generated slug is one this application would accept")
    void generatedSlugsAreAlwaysValid() {
        for (String name : new String[]{"Classic Aviator", "太陽眼鏡", "2024", "admin", "!!!",
                "a".repeat(300), "Ray-Ban® (Gold/Green)", null}) {
            String slug = ProductSlugs.generate(name);
            assertTrue(ProductSlugs.isValid(slug), "generate(" + name + ") = " + slug);
        }
    }

    @Test
    @DisplayName("a fresh suffix differs each time, so a collision retry makes progress")
    void freshSuffixesDiffer() {
        // The retry loop in the service depends on this: if withFreshSuffix were deterministic it
        // would propose the same taken slug forever and the loop would spin to its cap.
        Set<String> seen = new HashSet<>();
        for (int attempt = 0; attempt < 200; attempt++) seen.add(ProductSlugs.withFreshSuffix("classic-aviator"));
        assertTrue(seen.size() > 190, "only " + seen.size() + " distinct suffixes in 200 draws");
    }

    @Test
    @DisplayName("re-suffixing replaces the previous suffix instead of stacking")
    void suffixDoesNotStack() {
        String once = ProductSlugs.withFreshSuffix("classic-aviator");
        String twice = ProductSlugs.withFreshSuffix(once);
        // "classic-aviator-aaaaaa-bbbbbb" would grow without bound across retries.
        assertEquals(once.length(), twice.length(), once + " -> " + twice);
    }

    @Test
    @DisplayName("the suffix alphabet contains no vowels and no lookalike characters")
    void suffixAlphabetIsSafe() {
        String suffixes = String.join("", java.util.stream.IntStream.range(0, 500)
                .mapToObj(index -> ProductSlugs.randomSuffix()).toList());
        for (char forbidden : "aeiou01l".toCharArray()) {
            assertFalse(suffixes.indexOf(forbidden) >= 0, "suffix alphabet leaked '" + forbidden + "'");
        }
    }

    @Test
    @DisplayName("numeric-id detection separates legacy ids from slugs")
    void numericIdDetection() {
        assertTrue(ProductSlugs.isNumericId("22"));
        assertFalse(ProductSlugs.isNumericId("classic-aviator"));
        assertFalse(ProductSlugs.isNumericId("22-classic"));
        assertFalse(ProductSlugs.isNumericId(null));
    }
}
