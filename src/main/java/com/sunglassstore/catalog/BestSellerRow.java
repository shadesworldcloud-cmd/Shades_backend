package com.sunglassstore.catalog;

import java.math.BigDecimal;

/**
 * One row of the Best Sellers aggregate: a product and its net successful sales.
 *
 * Spring Data projection — the native query in ProductRepository aliases its columns to these
 * names.
 */
public interface BestSellerRow {

    Long getProductId();

    /** Units sold on eligible orders, minus units that came back. Never negative. */
    Long getSoldQuantity();

    /** Revenue for those retained units, used only as the first tie-breaker. */
    BigDecimal getSoldRevenue();
}
