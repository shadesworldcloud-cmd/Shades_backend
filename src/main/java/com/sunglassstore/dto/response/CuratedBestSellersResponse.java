package com.sunglassstore.dto.response;

import java.util.List;

/**
 * Admin view of the Best Sellers section.
 *
 * `curated` is what will render, in order. `sourceIsCurated` says whether the storefront is showing
 * that list or falling back to the sales ranking, so the admin screen never has to infer it —
 * a curated list whose every product has since been deactivated renders nothing, and the admin
 * needs to be told that rather than left guessing.
 */
public record CuratedBestSellersResponse(
        boolean sourceIsCurated,
        List<ProductResponse> curated,
        List<Long> missingProductIds) {
}
