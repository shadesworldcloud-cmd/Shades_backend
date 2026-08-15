package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateReviewRequest;
import com.sunglassstore.dto.request.UpdateReviewRequest;
import com.sunglassstore.dto.response.ReviewResponse;
import com.sunglassstore.dto.response.AdminReviewResponse;
import com.sunglassstore.entity.Product;
import com.sunglassstore.entity.Review;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.ReviewStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.OrderItemRepository;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.ReviewRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.repository.ReturnItemRepository;
import com.sunglassstore.repository.RefundRepository;
import com.sunglassstore.service.ReviewService;
import com.sunglassstore.service.NotificationService;
import com.sunglassstore.entity.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.sunglassstore.dto.response.ReviewableVariantResponse;
import com.sunglassstore.entity.OrderItem;
import com.sunglassstore.entity.enums.OrderStatus;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    /** What a shopper may see: published, plus anything a moderator explicitly approved. */
    private static final java.util.List<ReviewStatus> VISIBLE_STATUSES =
            java.util.List.of(ReviewStatus.PUBLISHED, ReviewStatus.APPROVED);

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnItemRepository returnItemRepository;
    private final RefundRepository refundRepository;
    private final NotificationService notificationService;
    private final com.sunglassstore.service.CommunicationPreferenceService communicationPreferences;

    @Override
    @Transactional
    public ReviewResponse createReview(Long userId, CreateReviewRequest request) {
        OrderItem orderItem = orderItemRepository.findById(request.getOrderItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchased variant not found"));
        // A deleted product has no variant to match against, and nothing to attach a review to, so
        // this fails the same way an unrelated order item does rather than throwing.
        if (!orderItem.getOrder().getUser().getUserId().equals(userId)
                || orderItem.getOrder().getOrderStatus() != OrderStatus.DELIVERED
                || orderItem.getVariant() == null
                || !orderItem.getVariant().getProduct().getProductId().equals(request.getProductId())
                || !isReviewable(orderItem)) {
            throw new BadRequestException("You can only review a delivered variant you purchased");
        }
        if (reviewRepository.existsByUserUserIdAndOrderItemOrderItemId(userId, request.getOrderItemId())) {
            throw new ConflictException("You have already reviewed this purchased variant");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setOrderItem(orderItem);
        review.setRating(request.getRating());
        review.setReviewText(cleanReviewText(request.getReviewText()));
        // Published immediately: eligibility is the gate, not a moderator.
        review.setReviewStatus(ReviewStatus.PUBLISHED);

        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long userId, Long reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findByReviewIdAndUserUserId(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        review.setRating(request.getRating());
        review.setReviewText(cleanReviewText(request.getReviewText()));
        // An edit to a live review stays live. An edit to one a moderator took down goes back into
        // the queue rather than straight back onto the page — otherwise a takedown could be undone
        // by simply retyping the text.
        review.setReviewStatus(review.getReviewStatus() == ReviewStatus.REJECTED
                ? ReviewStatus.PENDING
                : ReviewStatus.PUBLISHED);

        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findByReviewIdAndUserUserId(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        reviewRepository.delete(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable) {
        // Everything a moderator has not taken down. PENDING and REJECTED stay hidden.
        return reviewRepository.findByProductProductIdAndReviewStatusIn(productId, VISIBLE_STATUSES, pageable)
                .map(ReviewResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyProductReviews(Long userId, Long productId) {
        return reviewRepository.findByUserUserIdAndProductProductIdOrderByCreatedAtDesc(userId, productId)
                .stream().map(ReviewResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewableVariantResponse> getReviewableVariants(Long userId, Long productId) {
        return orderItemRepository.findDeliveredByUserAndProduct(userId, productId).stream()
                // A line whose product has been deleted cannot be reviewed — there is no product
                // page for the review to appear on. Filtered here rather than null-guarded in the
                // response so the customer is never offered a review that createReview would refuse.
                .filter(item -> item.getVariant() != null)
                .filter(this::isReviewable)
                .filter(item -> !reviewRepository.existsByUserUserIdAndOrderItemOrderItemId(userId, item.getOrderItemId()))
                .map(ReviewableVariantResponse::fromEntity).toList();
    }

    @Override
    @Transactional
    public ReviewResponse updateReviewStatus(Long reviewId, ReviewStatus status) {
        if (status == null || status == ReviewStatus.PENDING) {
            throw new BadRequestException("A review can only be approved or rejected during moderation");
        }
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (review.getReviewStatus() == status) {
            throw new BadRequestException("Review is already " + status.name().toLowerCase());
        }
        review.setReviewStatus(status);
        Review saved = reviewRepository.save(review);
        if (communicationPreferences.allowsInApp(saved.getUser().getUserId(),
                com.sunglassstore.service.CommunicationPreferenceService.Topic.REVIEW)) {
        notificationService.createNotification(saved.getUser().getUserId(), NotificationType.IN_APP,
                "Review " + status.name().toLowerCase() + " · Product #" + saved.getProduct().getProductId(),
                status == ReviewStatus.APPROVED
                        ? "Your review for " + saved.getProduct().getProductName() + " is now visible to shoppers."
                        : "Your review for " + saved.getProduct().getProductName() + " was not published. You can edit and resubmit it.");
        }
        return ReviewResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminReviewResponse> getReviewsForModeration(ReviewStatus status, String search, Pageable pageable) {
        String normalizedSearch = search == null ? "" : search.trim();
        return reviewRepository.searchForModeration(status, normalizedSearch, pageable)
                .map(AdminReviewResponse::fromEntity);
    }

    private String cleanReviewText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private boolean isReviewable(OrderItem item) {
        int returnedQuantity = returnItemRepository.sumReturnedQuantityByOrderItemId(item.getOrderItemId());
        boolean fullyReturned = returnedQuantity >= item.getQuantity();
        boolean orderRefunded = refundRepository.hasActiveStandaloneRefundForOrder(item.getOrder().getOrderId());
        return !fullyReturned && !orderRefunded;
    }
}
