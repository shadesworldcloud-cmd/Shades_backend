package com.sunglassstore.repository;

import com.sunglassstore.entity.Review;
import com.sunglassstore.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductProductIdAndReviewStatus(Long productId, ReviewStatus status, Pageable pageable);

    /** Publicly visible reviews for a product. */
    Page<Review> findByProductProductIdAndReviewStatusIn(Long productId, java.util.Collection<ReviewStatus> statuses, Pageable pageable);

    Optional<Review> findByReviewIdAndUserUserId(Long reviewId, Long userId);

    List<Review> findByUserUserIdAndProductProductIdOrderByCreatedAtDesc(Long userId, Long productId);

    boolean existsByUserUserIdAndProductProductId(Long userId, Long productId);
    boolean existsByUserUserIdAndOrderItemOrderItemId(Long userId, Long orderItemId);

    @Query(value = "SELECT r FROM Review r JOIN r.user u JOIN r.product p LEFT JOIN r.orderItem oi " +
            "LEFT JOIN oi.variant v WHERE (:status IS NULL OR r.reviewStatus = :status) AND " +
            "(:search = '' OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(COALESCE(r.reviewText, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(COALESCE(v.sku, '')) LIKE LOWER(CONCAT('%', :search, '%')))",
            countQuery = "SELECT COUNT(r) FROM Review r JOIN r.user u JOIN r.product p LEFT JOIN r.orderItem oi " +
                    "LEFT JOIN oi.variant v WHERE (:status IS NULL OR r.reviewStatus = :status) AND " +
                    "(:search = '' OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "LOWER(COALESCE(r.reviewText, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "LOWER(COALESCE(v.sku, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Review> searchForModeration(@Param("status") ReviewStatus status, @Param("search") String search, Pageable pageable);
}
