package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * The Best Sellers section as the administrator arranged it: product ids in display order.
 *
 * An empty list is meaningful and therefore allowed — it clears the curation and hands the section
 * back to the sales ranking. The size ceiling matches MAX_BEST_SELLERS in ProductServiceImpl so a
 * caller cannot store an order longer than the API will ever serve.
 */
@Getter
@Setter
public class CuratedBestSellersRequest {

    @NotNull(message = "productIds is required; send an empty list to clear the curated order")
    @Size(max = 50, message = "At most 50 products can be pinned to Best Sellers")
    private List<Long> productIds;
}
