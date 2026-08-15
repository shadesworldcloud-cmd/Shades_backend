package com.sunglassstore.controller;

import com.sunglassstore.dto.response.WishlistResponse;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlists")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<WishlistResponse> getWishlist(@AuthenticationPrincipal SecurityUser principal,
                                                 @RequestParam(defaultValue = "DEFAULT") String name) {
        return ResponseEntity.ok(wishlistService.getOrCreateWishlist(principal.getUserId(), name));
    }

    @PostMapping("/items/{productId}")
    public ResponseEntity<WishlistResponse> addItem(@AuthenticationPrincipal SecurityUser principal,
                                             @PathVariable Long productId,
                                             @RequestParam(defaultValue = "DEFAULT") String wishlistName) {
        return ResponseEntity.ok(wishlistService.addItem(principal.getUserId(), productId, wishlistName));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<WishlistResponse> removeItem(@AuthenticationPrincipal SecurityUser principal,
                                                @PathVariable Long productId,
                                                @RequestParam(defaultValue = "DEFAULT") String wishlistName) {
        return ResponseEntity.ok(wishlistService.removeItem(principal.getUserId(), productId, wishlistName));
    }
}
