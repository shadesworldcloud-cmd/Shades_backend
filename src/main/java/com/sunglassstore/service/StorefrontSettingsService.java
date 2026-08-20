package com.sunglassstore.service;

import java.util.List;

/**
 * Administrator-controlled storefront settings, stored as rows in CONFIG.
 *
 * Two settings so far, both driving the home page: the hero image and the curated Best Sellers
 * order.
 */
public interface StorefrontSettingsService {

    /** The uploaded hero image URL, or null when the bundled default should be used. */
    String getHeroImageUrl();

    /** Stores the hero image URL. */
    void setHeroImageUrl(String url);

    /** Forgets the uploaded hero image so the storefront falls back to the bundled default. */
    void clearHeroImage();

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
