package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Review;
import com.sunglassstore.entity.enums.ReviewStatus;

import java.time.LocalDateTime;

public record AdminReviewResponse(
        Long reviewId, Long productId, String productName, Long orderItemId,
        Long variantId, String variantName, String variantSku,
        Long userId, String customerName, String customerEmail,
        Integer rating, String reviewText, ReviewStatus reviewStatus,
        LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static AdminReviewResponse fromEntity(Review review) {
        var orderItem = review.getOrderItem();
        var variant = orderItem == null ? null : orderItem.getVariant();
        return new AdminReviewResponse(review.getReviewId(), review.getProduct().getProductId(),
                review.getProduct().getProductName(), orderItem == null ? null : orderItem.getOrderItemId(),
                variant == null ? null : variant.getVariantId(), variant == null ? null : variant.getVariantName(),
                variant == null ? null : variant.getSku(), review.getUser().getUserId(), review.getUser().getName(),
                review.getUser().getEmail(), review.getRating(), review.getReviewText(), review.getReviewStatus(),
                review.getCreatedAt(), review.getUpdatedAt());
    }
}
