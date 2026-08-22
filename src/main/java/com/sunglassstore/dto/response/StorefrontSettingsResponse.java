package com.sunglassstore.dto.response;

import java.util.Map;

/**
 * Public storefront appearance settings.
 *
 * heroImageUrl is null or blank when no administrator has uploaded one; the storefront then shows
 * its bundled default rather than an empty banner.
 *
 * collectionImageUrls is keyed by collection name (Men, Women, Unisex, Accessory) and contains only
 * the collections an administrator has actually given a photograph. A missing key means "use the
 * bundled asset" — and for Accessory, which ships without one, it means the flat tone.
 *
 * collectionImageUrls was added after heroImageUrl; it is an additional field rather than a
 * replacement, so a client reading only heroImageUrl is unaffected.
 */
public record StorefrontSettingsResponse(String heroImageUrl, Map<String, String> collectionImageUrls) {
}
