package com.sunglassstore.repository;

import com.sunglassstore.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByIsActiveTrue(Pageable pageable);

    /**
     * Resolve a product by its public slug. Served by UQ_PRODUCTS_SLUG, so this is an index lookup
     * rather than a scan — it is on the hot path for every product page view.
     *
     * Deliberately NOT filtered to active products: the service decides what an inactive product
     * means for the caller (404 for a customer, visible for an admin preview). Baking the filter in
     * here would make an admin previewing a draft indistinguishable from a typo'd slug.
     */
    java.util.Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** The slug for a numeric id, without loading the row — all the legacy redirect needs. */
    @Query("SELECT p.slug FROM Product p WHERE p.productId = :productId")
    java.util.Optional<String> findSlugByProductId(Long productId);

    /**
     * Which of these products sit in any of these categories.
     *
     * Used to resolve a category-scoped automatic offer against the handful of products in one cart,
     * rather than loading Product.categories for each of them (that association is lazy, and
     * touching it per line is one query per product). Returns product ids only because eligibility
     * is all the caller needs.
     */
    @Query(value = """
            SELECT DISTINCT pc.PRODUCT_ID FROM PRODUCT_CATEGORIES pc
            WHERE pc.PRODUCT_ID IN (:productIds) AND pc.CATEGORY_ID IN (:categoryIds)
            """, nativeQuery = true)
    java.util.List<Long> findProductIdsInCategories(
            @org.springframework.data.repository.query.Param("productIds") java.util.Collection<Long> productIds,
            @org.springframework.data.repository.query.Param("categoryIds") java.util.Collection<Long> categoryIds);

    @Query(value = "SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN p.variants v ON v.isActive = true " +
           "LEFT JOIN p.categories c " +
           "LEFT JOIN p.attributes a " +
           "WHERE p.isActive = true AND " +
           "(LOWER(COALESCE(p.productName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(p.productDescription, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(v.variantName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(v.sku, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(c.categoryName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(a.attributeValue, '')) LIKE LOWER(CONCAT('%', :query, '%')))",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Product p " +
                   "LEFT JOIN p.variants v ON v.isActive = true LEFT JOIN p.categories c LEFT JOIN p.attributes a " +
                   "WHERE p.isActive = true AND " +
                   "(LOWER(COALESCE(p.productName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(p.productDescription, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(v.variantName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(v.sku, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(c.categoryName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(a.attributeValue, '')) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> search(String query, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.categoryId = :categoryId AND p.isActive = true")
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    /**
     * Best Sellers: net units sold per product, ranked.
     *
     * Native because it is one aggregate over four tables and must stay one query — the alternative
     * (load products, count each) is a per-product round trip that grows with the catalogue.
     *
     * The two subtle correctness properties, both of which are about NOT joining:
     *
     * 1. PAYMENTS is reached through EXISTS, never a JOIN. A duplicated payment callback leaves two
     *    PAID rows for one order; joining them would multiply every ORDER_ITEM row and double the
     *    order's contribution. EXISTS asks a yes/no question and cannot fan out.
     * 2. RETURN_ITEMS is aggregated to one row per order item in a derived table BEFORE it is
     *    joined. An item returned across two separate returns has two rows; joining them raw would
     *    both multiply the sale and subtract each return once per duplicate.
     *
     * Eligibility:
     *   - the order is not CANCELLED (which is also what an expired/abandoned checkout becomes), and
     *   - it has a payment in PAID or PARTIALLY_REFUNDED. That is the project's existing definition
     *     of a settled order — see OrderServiceImpl.expireUnpaidOrder, which refuses to expire an
     *     order matching exactly this predicate. PENDING, AUTHORIZED, FAILED and CANCELLED payments
     *     are therefore excluded, and so is a fully REFUNDED one: money entirely returned is not a
     *     successful sale, which also covers a goodwill refund that never produced a return row.
     *
     * Returns are subtracted at RETURN_STATUS in (RECEIVED, COMPLETED) — RECEIVED is the point
     * ReturnServiceImpl puts the units back into inventory, so it is the point the sale is undone.
     * A merely REQUESTED or APPROVED return has not come back yet and still counts as sold.
     *
     * GREATEST(..., 0) clamps a per-item over-return, and HAVING drops products whose sales were
     * returned in full. Ordering is quantity DESC, then revenue DESC, then PRODUCT_ID ASC so the
     * ranking is total and stable across calls.
     *
     * Public eligibility of the product itself is applied here rather than in Java so that LIMIT
     * returns a full page: active, published, and holding at least one active in-stock variant.
     */
    @Query(value = """
            SELECT sold.PRODUCT_ID       AS productId,
                   SUM(sold.NET_QTY)     AS soldQuantity,
                   SUM(sold.NET_REVENUE) AS soldRevenue
            FROM (
                SELECT v.PRODUCT_ID AS PRODUCT_ID,
                       GREATEST(oi.QUANTITY - COALESCE(ret.RETURNED_QTY, 0), 0) AS NET_QTY,
                       GREATEST(oi.QUANTITY - COALESCE(ret.RETURNED_QTY, 0), 0) * oi.UNIT_PRICE AS NET_REVENUE
                FROM ORDER_ITEMS oi
                JOIN ORDERS o ON o.ORDER_ID = oi.ORDER_ID
                JOIN PRODUCT_VARIANTS v ON v.VARIANT_ID = oi.VARIANT_ID
                LEFT JOIN (
                    SELECT ri.ORDER_ITEM_ID AS ORDER_ITEM_ID, SUM(ri.QUANTITY) AS RETURNED_QTY
                    FROM RETURN_ITEMS ri
                    JOIN RETURNS r ON r.RETURN_ID = ri.RETURN_ID
                    WHERE r.RETURN_STATUS IN ('RECEIVED', 'COMPLETED')
                    GROUP BY ri.ORDER_ITEM_ID
                ) ret ON ret.ORDER_ITEM_ID = oi.ORDER_ITEM_ID
                WHERE o.ORDER_STATUS <> 'CANCELLED'
                  AND EXISTS (
                      SELECT 1 FROM PAYMENTS pay
                      WHERE pay.ORDER_ID = o.ORDER_ID
                        AND pay.PAYMENT_STATUS IN ('PAID', 'PARTIALLY_REFUNDED')
                  )
            ) sold
            JOIN PRODUCTS p ON p.PRODUCT_ID = sold.PRODUCT_ID
            WHERE p.IS_ACTIVE = 1
              AND p.PUBLISHED_AT IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM PRODUCT_VARIANTS av
                  WHERE av.PRODUCT_ID = p.PRODUCT_ID
                    AND av.IS_ACTIVE = 1
                    AND av.QUANTITY_AVAILABLE > 0
              )
            GROUP BY sold.PRODUCT_ID
            HAVING SUM(sold.NET_QTY) > 0
            ORDER BY soldQuantity DESC, soldRevenue DESC, sold.PRODUCT_ID ASC
            LIMIT :limit
            """, nativeQuery = true)
    java.util.List<com.sunglassstore.catalog.BestSellerRow> findBestSellers(int limit);
}
