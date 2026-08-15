package com.sunglassstore.dto.response;

import com.sunglassstore.entity.OrderItem;

public record ReviewableVariantResponse(Long orderItemId, Long variantId, String variantName, String sku,
                                        String productName, Integer quantity) {
    public static ReviewableVariantResponse fromEntity(OrderItem item) {
        return new ReviewableVariantResponse(item.getOrderItemId(), item.getVariant().getVariantId(),
                item.getVariant().getVariantName(), item.getSku(), item.getProductName(), item.getQuantity());
    }
}
