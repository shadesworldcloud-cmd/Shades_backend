package com.sunglassstore.service;

import com.sunglassstore.dto.response.WishlistResponse;

public interface WishlistService {
    WishlistResponse getOrCreateWishlist(Long userId, String name);
    WishlistResponse addItem(Long userId, Long productId, String wishlistName);
    WishlistResponse removeItem(Long userId, Long productId, String wishlistName);
}
