package com.sunglassstore.service;

import com.sunglassstore.entity.Product;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.Wishlist;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.WishlistItemRepository;
import com.sunglassstore.repository.WishlistRepository;
import com.sunglassstore.service.impl.WishlistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WishlistServiceImplTest {
    private WishlistRepository wishlists;
    private WishlistItemRepository items;
    private ProductRepository products;
    private UserService users;
    private WishlistServiceImpl service;

    @BeforeEach
    void setUp() {
        wishlists = mock(WishlistRepository.class); items = mock(WishlistItemRepository.class);
        products = mock(ProductRepository.class); users = mock(UserService.class);
        service = new WishlistServiceImpl(wishlists, items, products, users);
    }

    @Test
    void getWishlistNormalizesNameAndCreatesForAuthenticatedUser() {
        User user = new User(); user.setUserId(5L); when(users.findById(5L)).thenReturn(user);
        when(wishlists.findByUserUserIdAndWishlistName(5L, "DEFAULT")).thenReturn(Optional.empty());
        when(wishlists.save(any(Wishlist.class))).thenAnswer(invocation -> { Wishlist value = invocation.getArgument(0); value.setWishlistId(8L); return value; });

        var response = service.getOrCreateWishlist(5L, " default ");

        assertEquals("DEFAULT", response.wishlistName());
        verify(wishlists).findByUserUserIdAndWishlistName(5L, "DEFAULT");
    }

    @Test
    void addRejectsInactiveProduct() {
        Wishlist wishlist = wishlist(); Product product = product(false);
        when(wishlists.findByUserUserIdAndWishlistName(5L, "DEFAULT")).thenReturn(Optional.of(wishlist));
        when(products.findById(3L)).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class, () -> service.addItem(5L, 3L, "DEFAULT"));
        verify(items, never()).save(any());
    }

    @Test
    void addRejectsDuplicateProduct() {
        Wishlist wishlist = wishlist(); Product product = product(true);
        when(wishlists.findByUserUserIdAndWishlistName(5L, "DEFAULT")).thenReturn(Optional.of(wishlist));
        when(products.findById(3L)).thenReturn(Optional.of(product));
        when(items.existsByWishlistWishlistIdAndProductProductId(8L, 3L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.addItem(5L, 3L, "DEFAULT"));
    }

    private Wishlist wishlist() {
        User user = new User(); user.setUserId(5L);
        Wishlist wishlist = new Wishlist(); wishlist.setWishlistId(8L); wishlist.setUser(user); wishlist.setWishlistName("DEFAULT");
        return wishlist;
    }

    private Product product(boolean active) {
        Product product = new Product(); product.setProductId(3L); product.setProductName("Barcelona");
        product.setBasePrice(new BigDecimal("999.00")); product.setIsActive(active); return product;
    }
}
