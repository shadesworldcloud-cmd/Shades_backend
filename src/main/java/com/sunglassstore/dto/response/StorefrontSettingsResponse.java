package com.sunglassstore.dto.response;

/**
 * Public storefront appearance settings.
 *
 * heroImageUrl is null or blank when no administrator has uploaded one; the storefront then shows
 * its bundled default rather than an empty banner.
 */
public record StorefrontSettingsResponse(String heroImageUrl) {
}
