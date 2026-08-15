package com.sunglassstore.repository;

import com.sunglassstore.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByUserUserIdAndWishlistName(Long userId, String wishlistName);
}
