package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Cart;

import java.util.List;

public record CartResponse(Long cartId, String status, List<CartLine> items) {
    public record CartLine(Long cartItemId, Long variantId, Long productId, Integer quantity) {}

    public static CartResponse fromEntity(Cart cart) {
        List<CartLine> lines = cart.getItems().stream()
                .map(item -> new CartLine(item.getCartItemId(), item.getVariant().getVariantId(),
                        item.getVariant().getProduct().getProductId(), item.getQuantity()))
                .toList();
        return new CartResponse(cart.getCartId(), cart.getCartStatus().name(), lines);
    }
}
