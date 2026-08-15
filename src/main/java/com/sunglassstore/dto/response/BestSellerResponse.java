package com.sunglassstore.dto.response;

import java.math.BigDecimal;

/**
 * One entry in the public Best Sellers ranking.
 *
 * The product is nested rather than flattened so that ProductResponse keeps a single meaning
 * everywhere else in the API — soldQuantity is a property of this ranking, not of the product.
 *
 * soldQuantity is exposed deliberately: it is the number the ranking is built from, so a test (or
 * a support query) can check the order against the database instead of taking it on trust.
 */
public record BestSellerResponse(ProductResponse product, long soldQuantity, BigDecimal soldRevenue) {
}
