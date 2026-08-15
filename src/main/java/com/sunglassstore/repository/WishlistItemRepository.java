package com.sunglassstore.repository;

import com.sunglassstore.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByWishlistWishlistIdAndProductProductId(Long wishlistId, Long productId);

    boolean existsByWishlistWishlistIdAndProductProductId(Long wishlistId, Long productId);
}
