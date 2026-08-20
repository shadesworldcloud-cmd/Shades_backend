package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CuratedBestSellersRequest;
import com.sunglassstore.dto.response.CuratedBestSellersResponse;
import com.sunglassstore.dto.response.ProductResponse;
import com.sunglassstore.dto.response.StorefrontSettingsResponse;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.service.ImageKitStorageService;
import com.sunglassstore.service.ProductService;
import com.sunglassstore.service.StorefrontSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Administrator control of the home page: which products are Best Sellers and in what order, and
 * the hero image.
 *
 * ADMIN-gated twice, deliberately. SecurityConfig lets ADMIN, SUPPORT and INVENTORY_MANAGER reach
 * /api/admin/**, which is wider than these endpoints should allow — changing the shop window is not
 * a support or stock job — so every method here also carries @PreAuthorize("hasRole('ADMIN')"). Same
 * belt-and-braces treatment as the automatic-offer admin routes.
 */
@RestController
@RequestMapping("/api/admin/storefront")
@RequiredArgsConstructor
public class AdminStorefrontController {

    private final StorefrontSettingsService storefrontSettingsService;
    private final ProductService productService;
    private final ImageKitStorageService imageStorageService;

    /** The curated order plus the products it names, so the admin screen can render cards. */
    @GetMapping("/best-sellers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CuratedBestSellersResponse> getCuratedBestSellers() {
        List<Long> curatedIds = storefrontSettingsService.getCuratedBestSellerIds();
        List<ProductResponse> products = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        for (Long id : curatedIds) {
            try {
                products.add(productService.getProductById(id));
            } catch (ResourceNotFoundException notFound) {
                // A pinned product that has since been deleted. Reported rather than hidden so the
                // administrator can see why their list is shorter on the storefront than here.
                //
                // Deliberately NOT catching RuntimeException: that would swallow a database or
                // mapping failure and report it to the administrator as "this product no longer
                // exists", which is a lie that hides an outage.
                missing.add(id);
            }
        }
        return ResponseEntity.ok(new CuratedBestSellersResponse(!curatedIds.isEmpty(), products, missing));
    }

    /** Replaces the curated order. An empty list clears it and restores the sales ranking. */
    @PutMapping("/best-sellers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CuratedBestSellersResponse> setCuratedBestSellers(
            @Valid @RequestBody CuratedBestSellersRequest request) {
        // Validate every id BEFORE storing any of them: a half-applied order would leave the section
        // showing a list the administrator never chose.
        for (Long id : request.getProductIds() == null ? List.<Long>of() : request.getProductIds()) {
            if (id != null) productService.getProductById(id);
        }
        storefrontSettingsService.setCuratedBestSellerIds(request.getProductIds());
        return getCuratedBestSellers();
    }

    @PostMapping("/hero-image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StorefrontSettingsResponse> uploadHeroImage(@RequestParam("file") MultipartFile file) {
        String url = imageStorageService.storeInFolder("/storefront/hero", file);
        storefrontSettingsService.setHeroImageUrl(url);
        return ResponseEntity.ok(new StorefrontSettingsResponse(url));
    }

    /** Reverts the home page to the bundled hero image. */
    @DeleteMapping("/hero-image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StorefrontSettingsResponse> resetHeroImage() {
        storefrontSettingsService.clearHeroImage();
        return ResponseEntity.ok(new StorefrontSettingsResponse(null));
    }
}
