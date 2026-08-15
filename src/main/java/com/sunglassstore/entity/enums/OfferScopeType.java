package com.sunglassstore.entity.enums;

/**
 * Which purchasable units an automatic offer covers.
 *
 * Categories are resolved through the existing PRODUCT_CATEGORIES join rather than copied onto the
 * offer, so moving a product between categories changes its eligibility for future carts without
 * touching the offer — and never changes an order that has already been placed, because orders
 * carry their own snapshot.
 */
public enum OfferScopeType {
    ALL_PRODUCTS,
    SELECTED_PRODUCTS,
    SELECTED_CATEGORIES
}
