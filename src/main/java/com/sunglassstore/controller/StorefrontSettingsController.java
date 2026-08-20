package com.sunglassstore.controller;

import com.sunglassstore.dto.response.StorefrontSettingsResponse;
import com.sunglassstore.service.StorefrontSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read of the storefront's appearance settings.
 *
 * Public because the hero image is the first thing on the home page and must render for a signed-out
 * visitor — the same reasoning that makes the automatic-offer banner endpoint public. It is a read
 * that returns one URL and accepts nothing; every write lives under /api/admin/storefront.
 */
@RestController
@RequestMapping("/api/storefront")
@RequiredArgsConstructor
public class StorefrontSettingsController {

    private final StorefrontSettingsService storefrontSettingsService;

    @GetMapping("/settings")
    public ResponseEntity<StorefrontSettingsResponse> getSettings() {
        return ResponseEntity.ok(new StorefrontSettingsResponse(storefrontSettingsService.getHeroImageUrl()));
    }
}
