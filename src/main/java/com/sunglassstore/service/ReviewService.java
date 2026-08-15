package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateReviewRequest;
import com.sunglassstore.dto.request.UpdateReviewRequest;
import com.sunglassstore.dto.response.ReviewResponse;
import com.sunglassstore.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import com.sunglassstore.dto.response.ReviewableVariantResponse;
import com.sunglassstore.dto.response.AdminReviewResponse;

public interface ReviewService {
    ReviewResponse createReview(Long userId, CreateReviewRequest request);
    ReviewResponse updateReview(Long userId, Long reviewId, UpdateReviewRequest request);
    void deleteReview(Long userId, Long reviewId);
    Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable);
    List<ReviewResponse> getMyProductReviews(Long userId, Long productId);
    List<ReviewableVariantResponse> getReviewableVariants(Long userId, Long productId);
    ReviewResponse updateReviewStatus(Long reviewId, ReviewStatus status);
    Page<AdminReviewResponse> getReviewsForModeration(ReviewStatus status, String search, Pageable pageable);
}
