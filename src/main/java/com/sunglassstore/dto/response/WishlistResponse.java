package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Product;
import com.sunglassstore.entity.ProductImage;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.Wishlist;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record WishlistResponse(Long wishlistId, String wishlistName, LocalDateTime createdAt, List<Item> items) {
    public static WishlistResponse fromEntity(Wishlist wishlist) {
        return new WishlistResponse(wishlist.getWishlistId(), wishlist.getWishlistName(), wishlist.getCreatedAt(),
                wishlist.getItems().stream()
                        .sorted(Comparator.comparing(item -> item.getAddedAt(), Comparator.reverseOrder()))
                        .map(item -> Item.fromProduct(item.getWishlistItemId(), item.getProduct(), item.getAddedAt()))
                        .toList());
    }

    /**
     * @param slug carried so the wishlist can link to a product without the storefront having to
     *             look it up in the catalogue listing. The wishlist renders straight from this
     *             response and never loads product_list, so without the slug here its links would
     *             be the only ones left on numeric ids.
     */
    public record Item(Long wishlistItemId, Long productId, String slug, String productName, String brand,
                       BigDecimal price, String imageUrl, String imageAlt, boolean active,
                       int quantityAvailable, LocalDateTime addedAt) {
        private static Item fromProduct(Long wishlistItemId, Product product, LocalDateTime addedAt) {
            ProductImage image = product.getImages().stream().filter(value -> Boolean.TRUE.equals(value.getIsPrimary()))
                    .findFirst().orElse(product.getImages().stream().findFirst().orElse(null));
            List<ProductVariant> activeVariants = product.getVariants().stream()
                    .filter(value -> Boolean.TRUE.equals(value.getIsActive())).toList();
            BigDecimal price = activeVariants.stream().map(ProductVariant::getPrice).min(BigDecimal::compareTo)
                    .orElse(product.getBasePrice());
            int stock = activeVariants.stream().mapToInt(value -> Math.max(0, value.getQuantityAvailable())).sum();
            return new Item(wishlistItemId, product.getProductId(), product.getSlug(), product.getProductName(), product.getBrand(), price,
                    image == null ? null : image.getImageUrl(), image == null ? product.getProductName() : image.getAltText(),
                    Boolean.TRUE.equals(product.getIsActive()), stock, addedAt);
        }
    }
}
