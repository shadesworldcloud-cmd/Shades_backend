package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CartItemRequest;
import com.sunglassstore.entity.Cart;
import com.sunglassstore.entity.CartItem;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.CartStatus;
import com.sunglassstore.dto.response.CartResponse;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.InsufficientInventoryException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.CartItemRepository;
import com.sunglassstore.repository.CartRepository;
import com.sunglassstore.repository.ProductVariantRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CartResponse getOrCreateCart(Long userId) {
        return CartResponse.fromEntity(getOrCreateCartEntity(userId));
    }

    private Cart getOrCreateCartEntity(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return cartRepository.findByUserUserIdAndCartStatusForUpdate(userId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setCartStatus(CartStatus.ACTIVE);
                    return cartRepository.save(cart);
                });
    }

    @Override
    @Transactional
    public CartResponse addItem(Long userId, CartItemRequest request) {
        Cart cart = getOrCreateCartEntity(userId);
        ProductVariant variant = validateVariant(request.getVariantId(), request.getQuantity());

        Optional<CartItem> existing = cartItemRepository
                .findByCartCartIdAndVariantVariantId(cart.getCartId(), request.getVariantId());

        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQuantity = item.getQuantity() + request.getQuantity();
            validateInventory(variant, newQuantity);
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setVariant(variant);
            item.setQuantity(request.getQuantity());
            cartItemRepository.save(item);
            cart.getItems().add(item);
        }

        return CartResponse.fromEntity(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItemQuantity(Long userId, Long variantId, Integer quantity) {
        Cart cart = getOrCreateCartEntity(userId);
        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in cart"));

        if (quantity == null) {
            throw new BadRequestException("Quantity is required");
        }
        if (quantity <= 0) {
            cartItemRepository.delete(item);
            cart.getItems().remove(item);
        } else {
            validateInventory(item.getVariant(), quantity);
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return CartResponse.fromEntity(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long variantId) {
        Cart cart = getOrCreateCartEntity(userId);
        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in cart"));
        cartItemRepository.delete(item);
        cart.getItems().remove(item);
        return CartResponse.fromEntity(cart);
    }

    @Override
    @Transactional
    public CartResponse clearCart(Long userId) {
        Cart cart = getOrCreateCartEntity(userId);
        cart.getItems().clear();
        return CartResponse.fromEntity(cartRepository.save(cart));
    }

    private ProductVariant validateVariant(Long variantId, int quantity) {
        if (quantity <= 0) throw new BadRequestException("Quantity must be at least 1");
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));

        if (!Boolean.TRUE.equals(variant.getIsActive()) || !Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
            throw new BadRequestException("This product is no longer available");
        }

        validateInventory(variant, quantity);
        return variant;
    }

    private void validateInventory(ProductVariant variant, int quantity) {
        if (variant.getQuantityAvailable() < quantity) {
            throw new InsufficientInventoryException(
                    "Only " + variant.getQuantityAvailable() + " units available for " + variant.getSku());
        }
    }
}
