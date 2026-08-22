package com.sunglassstore.service;

import com.sunglassstore.exception.BadRequestException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Administrator-controlled storefront settings, stored as rows in CONFIG.
 *
 * Three settings: the hero image, the curated Best Sellers order, and a photograph per collection.
 * All of them drive the home page, and the collection photographs drive /collections too.
 */
public interface StorefrontSettingsService {

    /**
     * The collections whose photograph an administrator may replace, in the order they are shown.
     *
     * A fixed list rather than "whatever categories exist", because this is the CONFIG key space:
     * accepting an arbitrary name would let a caller create unbounded rows in a table that is read
     * on every home page load. It matches StorefrontCategoryBootstrap's REQUIRED_CATEGORIES, which
     * cannot be reused directly — it is private and its class is @Profile("!test").
     */
    List<String> COLLECTIONS = List.of("Men", "Women", "Unisex", "Accessory");

    /**
     * Canonicalises a collection name, accepting any casing, and rejects anything else.
     *
     * Case-insensitive because the storefront routes are lower-case (/collections/men) while the
     * category names are capitalised, and an administrator screen posting either spelling should
     * not have to know which. Rejection is a BadRequestException rather than a silent no-op: a
     * typo that quietly changed nothing would look to the administrator like a failed upload with
     * no reason given.
     */
    static String requireCollection(String name) {
        String trimmed = name == null ? "" : name.trim();
        for (String collection : COLLECTIONS) {
            if (collection.equalsIgnoreCase(trimmed)) return collection;
        }
        throw new BadRequestException("Unknown collection '" + trimmed + "'. Expected one of "
                + String.join(", ", COLLECTIONS) + ".");
    }

    /** The CONFIG key holding one collection's photograph. */
    static String collectionImageKey(String canonicalCollection) {
        return "home.collection." + canonicalCollection.toLowerCase(Locale.ROOT) + ".image_url";
    }

    /** The uploaded hero image URL, or null when the bundled default should be used. */
    String getHeroImageUrl();

    /** Stores the hero image URL. */
    void setHeroImageUrl(String url);

    /** Forgets the uploaded hero image so the storefront falls back to the bundled default. */
    void clearHeroImage();

    /**
     * Configured collection photographs, keyed by canonical collection name.
     *
     * Only collections an administrator has actually set appear. A reverted collection is ABSENT
     * rather than present-and-blank — unlike getHeroImageUrl, which returns "" for the same state,
     * because a map lets the storefront express "use the bundled asset" as one missing-key check
     * instead of a null check and a blank check.
     */
    Map<String, String> getCollectionImageUrls();

    /** Stores one collection's photograph. Throws if the collection is not one of COLLECTIONS. */
    void setCollectionImageUrl(String collection, String url);

    /** Forgets one collection's photograph so the storefront falls back to its bundled asset. */
    void clearCollectionImage(String collection);

    /**
     * Product ids the administrator pinned to Best Sellers, in the order they must appear.
     * Empty means "not curated" — the sales ranking then decides, exactly as before.
     */
    List<Long> getCuratedBestSellerIds();

    /**
     * Replaces the curated order wholesale. Duplicates are dropped keeping first position; an empty
     * list hands the section back to the sales ranking.
     */
    List<Long> setCuratedBestSellerIds(List<Long> productIds);
}
