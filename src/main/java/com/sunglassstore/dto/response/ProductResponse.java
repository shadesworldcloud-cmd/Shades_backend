package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Product;
import com.sunglassstore.entity.ProductAttribute;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class ProductResponse {
    /**
     * Still present, and deliberately so. The public URL no longer carries it, which is what the
     * change was for, but the cart, wishlist, review and inventory APIs all address a product by
     * this id and every one of them authorises the caller independently. Re-keying those APIs onto
     * the slug would be a large change that buys no additional protection — an id is not a
     * credential in either scheme. See ProductSlugs for the same point stated the other way round.
     */
    private Long productId;
    /** The public identifier. This, not productId, is what a storefront URL must be built from. */
    private String slug;
    private String productName;
    private String productDescription;
    private String brand;
    private BigDecimal basePrice;
    private Boolean isActive;
    /** For the admin editor's read-edit-save conflict check. Meaningless to the storefront. */
    private Long version;
    private java.time.Instant publishedAt;
    /**
     * Canonical answer to "does this product get a New badge", decided by NewProductPolicy on the
     * server. Clients must render this rather than recomputing an age from a timestamp: the badge
     * has to be identical on the home page, Shop, Collections, every listing and the product page,
     * and it cannot depend on the customer's system clock.
     */
    private Boolean isNew;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<CategorySummary> categories;
    private List<VariantSummary> variants;
    private List<ImageSummary> images;
    private Map<String, String> attributes;

    /**
     * @param isNew decided by NewProductPolicy. Required rather than defaulted so a new call site
     *              cannot quietly ship a response whose badge is always false.
     */
    public static ProductResponse fromEntity(Product product, boolean isNew) {
        ProductResponse response = new ProductResponse();
        response.setProductId(product.getProductId());
        response.setSlug(product.getSlug());
        response.setProductName(product.getProductName());
        response.setProductDescription(product.getProductDescription());
        response.setBrand(product.getBrand());
        response.setBasePrice(product.getBasePrice());
        response.setIsActive(product.getIsActive());
        response.setVersion(product.getVersion());
        response.setPublishedAt(product.getPublishedAt());
        response.setIsNew(isNew);
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        response.setCategories(product.getCategories().stream().map(category ->
                new CategorySummary(category.getCategoryId(), category.getCategoryName())).collect(Collectors.toSet()));
        response.setVariants(product.getVariants().stream().map(VariantSummary::fromEntity).toList());
        response.setImages(product.getImages().stream().map(ImageSummary::fromEntity).toList());
        response.setAttributes(product.getAttributes().stream().filter(attribute -> attribute.getVariant() == null).collect(Collectors.toMap(
                attribute -> attribute.getAttributeName(), attribute -> attribute.getAttributeValue(), (first, second) -> second)));
        return response;
    }

    public record CategorySummary(Long categoryId, String categoryName) {}

    /**
     * @param position    the family order, 1..N; the list arrives sorted by it.
     * @param mainVariant canonical "this is the Main Product / Variant 1" designation — always
     *                    position 1. Sent explicitly so no client re-derives it and drifts.
     */
    public record VariantSummary(Long variantId, Integer position, Boolean mainVariant,
                                 String sku, String variantName, String variantDescription,
                                 BigDecimal price, Integer quantityAvailable, Integer lowStockThreshold, Boolean isActive,
                                 Map<String, String> attributes) {
        public static VariantSummary fromEntity(com.sunglassstore.entity.ProductVariant variant) {
            return new VariantSummary(variant.getVariantId(), variant.getPosition(), variant.isMainVariant(),
                    variant.getSku(), variant.getVariantName(),
                    variant.getVariantDescription(),
                    variant.getPrice(), variant.getQuantityAvailable(), variant.getLowStockThreshold(), variant.getIsActive(),
                    variant.getAttributes().stream().collect(Collectors.toMap(ProductAttribute::getAttributeName,
                            ProductAttribute::getAttributeValue, (first, second) -> second)));
        }
    }

    /**
     * @param imageId  internal id, kept because the admin image endpoints address images by it.
     * @param publicId the identifier storefront markup should key on — see ProductImage.publicId.
     * @param variantId now read from the VARIANT_ID column. It used to be recovered by matching
     *                  "/variants/(\d+)/" against imageUrl, so the association was a property of
     *                  the file's storage path: relocating the upload directory or putting a CDN in
     *                  front of it unlinked every variant photo at once.
     */
    public record ImageSummary(Long imageId, String publicId, String imageUrl, String altText,
                               Integer displayOrder, Boolean isPrimary, Long variantId) {
        public static ImageSummary fromEntity(com.sunglassstore.entity.ProductImage image) {
            return new ImageSummary(image.getImageId(), image.getPublicId(), image.getImageUrl(),
                    image.getAltText(), image.getDisplayOrder(), image.getIsPrimary(), image.getVariantId());
        }
    }
}
