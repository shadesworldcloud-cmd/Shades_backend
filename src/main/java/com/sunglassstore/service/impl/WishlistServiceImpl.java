package com.sunglassstore.service.impl;

import com.sunglassstore.entity.*;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.WishlistItemRepository;
import com.sunglassstore.repository.WishlistRepository;
import com.sunglassstore.service.UserService;
import com.sunglassstore.service.WishlistService;
import com.sunglassstore.dto.response.WishlistResponse;
import com.sunglassstore.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final UserService userService;

    @Override
    @Transactional
    public WishlistResponse getOrCreateWishlist(Long userId, String name) {
        return WishlistResponse.fromEntity(getOrCreateEntity(userId, normalizeName(name)));
    }

    private Wishlist getOrCreateEntity(Long userId, String name) {
        return wishlistRepository.findByUserUserIdAndWishlistName(userId, name)
                .orElseGet(() -> {
                    User user = userService.findById(userId);
                    Wishlist wishlist = new Wishlist();
                    wishlist.setUser(user);
                    wishlist.setWishlistName(name);
                    return wishlistRepository.save(wishlist);
                });
    }

    @Override
    @Transactional
    public WishlistResponse addItem(Long userId, Long productId, String wishlistName) {
        Wishlist wishlist = getOrCreateEntity(userId, normalizeName(wishlistName));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new BadRequestException("Inactive products cannot be added to a wishlist");
        }

        if (wishlistItemRepository.existsByWishlistWishlistIdAndProductProductId(
                wishlist.getWishlistId(), productId)) {
            throw new ConflictException("Product is already in this wishlist");
        }

        WishlistItem item = new WishlistItem();
        item.setWishlist(wishlist);
        item.setProduct(product);
        wishlist.getItems().add(wishlistItemRepository.save(item));

        return WishlistResponse.fromEntity(wishlist);
    }

    @Override
    @Transactional
    public WishlistResponse removeItem(Long userId, Long productId, String wishlistName) {
        String normalizedName = normalizeName(wishlistName);
        Wishlist wishlist = wishlistRepository.findByUserUserIdAndWishlistName(userId, normalizedName)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist not found"));
        WishlistItem item = wishlistItemRepository
                .findByWishlistWishlistIdAndProductProductId(wishlist.getWishlistId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in wishlist"));
        wishlistItemRepository.delete(item);
        wishlist.getItems().removeIf(value -> value.getWishlistItemId().equals(item.getWishlistItemId()));

        return WishlistResponse.fromEntity(wishlist);
    }

    private String normalizeName(String name) {
        String normalized = name == null || name.isBlank() ? "DEFAULT" : name.trim().toUpperCase();
        if (normalized.length() > 100) throw new BadRequestException("Wishlist name cannot exceed 100 characters");
        return normalized;
    }
}
