package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CreateReviewRequest;
import com.sunglassstore.dto.request.UpdateReviewRequest;
import com.sunglassstore.dto.request.ModerateReviewRequest;
import com.sunglassstore.dto.response.ReviewResponse;
import com.sunglassstore.dto.response.AdminReviewResponse;
import com.sunglassstore.entity.enums.ReviewStatus;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.sunglassstore.dto.response.ReviewableVariantResponse;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/products/{productId}")
    public ResponseEntity<Page<ReviewResponse>> getProductReviews(@PathVariable Long productId, Pageable pageable) {
        return ResponseEntity.ok(reviewService.getProductReviews(productId, pageable));
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(@AuthenticationPrincipal SecurityUser principal,
                                                @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.createReview(principal.getUserId(), request));
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(@AuthenticationPrincipal SecurityUser principal,
                                                @PathVariable Long reviewId,
                                                @Valid @RequestBody UpdateReviewRequest request) {
        return ResponseEntity.ok(reviewService.updateReview(principal.getUserId(), reviewId, request));
    }

    @GetMapping("/products/{productId}/mine")
    public ResponseEntity<List<ReviewResponse>> getMyProductReview(@AuthenticationPrincipal SecurityUser principal,
                                                            @PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getMyProductReviews(principal.getUserId(), productId));
    }

    @GetMapping("/products/{productId}/reviewable-variants")
    public ResponseEntity<List<ReviewableVariantResponse>> getReviewableVariants(
            @AuthenticationPrincipal SecurityUser principal, @PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getReviewableVariants(principal.getUserId(), productId));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(@AuthenticationPrincipal SecurityUser principal,
                                              @PathVariable Long reviewId) {
        reviewService.deleteReview(principal.getUserId(), reviewId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/admin/{reviewId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<ReviewResponse> updateReviewStatus(@PathVariable Long reviewId,
                                                      @Valid @RequestBody ModerateReviewRequest request) {
        return ResponseEntity.ok(reviewService.updateReviewStatus(reviewId, request.getStatus()));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<Page<AdminReviewResponse>> getReviewsForModeration(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "") String search,
            Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviewsForModeration(status, search, pageable));
    }
}
