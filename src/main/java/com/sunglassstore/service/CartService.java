package com.sunglassstore.service;

import com.sunglassstore.dto.request.CartItemRequest;
import com.sunglassstore.dto.response.CartResponse;

public interface CartService {
    CartResponse getOrCreateCart(Long userId);
    CartResponse addItem(Long userId, CartItemRequest request);
    CartResponse updateItemQuantity(Long userId, Long variantId, Integer quantity);
    CartResponse removeItem(Long userId, Long variantId);
    CartResponse clearCart(Long userId);
}
