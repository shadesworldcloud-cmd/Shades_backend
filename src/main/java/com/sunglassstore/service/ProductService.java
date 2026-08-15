package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateImageRequest;
import com.sunglassstore.dto.request.CreateProductRequest;
import com.sunglassstore.dto.request.CreateVariantRequest;
import com.sunglassstore.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    Page<ProductResponse> getAllActiveProducts(Pageable pageable);
    Page<ProductResponse> getAllProducts(Pageable pageable);
    ProductResponse getProductById(Long productId);

    /** Public resolution by slug. 404s for an unknown slug and for an inactive product alike. */
    ProductResponse getProductBySlug(String slug);

    /** The slug a legacy numeric /product/{id} link should redirect to. */
    String findCanonicalSlug(Long productId);
    Page<ProductResponse> searchProducts(String keyword, Pageable pageable);
    Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable);

    /**
     * Public Best Sellers ranking, highest net sales first. See ProductRepository.findBestSellers
     * for the eligibility, refund and tie-breaking rules.
     */
    java.util.List<com.sunglassstore.dto.response.BestSellerResponse> getBestSellers(int limit);
    ProductResponse createProduct(CreateProductRequest request);
    ProductResponse updateProduct(Long productId, CreateProductRequest request);
    void deleteProduct(Long productId);
    ProductResponse setProductActive(Long productId, boolean active);
    ProductResponse.VariantSummary addVariant(Long productId, CreateVariantRequest request);
    ProductResponse.VariantSummary updateVariant(Long productId, Long variantId, CreateVariantRequest request);

    /**
     * Deletes a never-ordered variant (its cart lines and stock ledger go with it; its photos move
     * to the Main Product). Refused for the last variant and for one with order history — archive
     * those instead.
     */
    void deleteVariant(Long productId, Long variantId);

    /** The deliberate "Set as Main Variant" workflow: moves the variant to position 1. */
    ProductResponse setMainVariant(Long productId, Long variantId);

    /** Archive (false) or restore (true) one variant without touching its data or history. */
    ProductResponse.VariantSummary setVariantActive(Long productId, Long variantId, boolean active);
    ProductResponse.ImageSummary addImage(Long productId, CreateImageRequest request);
    void deleteImage(Long productId, Long imageId);

    /** Replaces the gallery order. The list must name exactly this product's images. */
    java.util.List<ProductResponse.ImageSummary> reorderImages(Long productId, java.util.List<Long> imageIdsInOrder);

    /** Promotes one image to primary and demotes the incumbent, in that order. */
    java.util.List<ProductResponse.ImageSummary> setPrimaryImage(Long productId, Long imageId);

    /** Edits alt text and variant association. Does not move the file. */
    ProductResponse.ImageSummary updateImage(Long productId, Long imageId, CreateImageRequest request);
}
