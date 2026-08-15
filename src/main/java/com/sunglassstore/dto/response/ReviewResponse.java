package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Review;
import com.sunglassstore.entity.enums.ReviewStatus;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long reviewId,
        Long productId,
        Long orderItemId,
        Long variantId,
        String variantName,
        String variantSku,
        String customerName,
        Integer rating,
        String reviewText,
        ReviewStatus reviewStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewResponse fromEntity(Review review) {
        var orderItem = review.getOrderItem();
        var variant = orderItem == null ? null : orderItem.getVariant();
        return new ReviewResponse(review.getReviewId(), review.getProduct().getProductId(),
                orderItem == null ? null : orderItem.getOrderItemId(), variant == null ? null : variant.getVariantId(),
                variant == null ? null : variant.getVariantName(), variant == null ? null : variant.getSku(),
                review.getUser().getName(), review.getRating(),
                review.getReviewText(), review.getReviewStatus(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
