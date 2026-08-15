package com.sunglassstore.repository;

import com.sunglassstore.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    java.util.List<ProductImage> findByProductProductId(Long productId);

    /**
     * One product's images in a total, stable order. IMAGE_ID breaks ties on DISPLAY_ORDER, which a
     * multi-file upload produces routinely — without it the gallery could come back in a different
     * order between two identical requests. Served by IDX_PRODUCT_IMAGES_ORDER without a filesort.
     */
    java.util.List<ProductImage> findByProductProductIdOrderByDisplayOrderAscImageIdAsc(Long productId);

    long countByProductProductId(Long productId);
}
