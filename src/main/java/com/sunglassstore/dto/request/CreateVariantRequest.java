package com.sunglassstore.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class CreateVariantRequest {

    private Long variantId;

    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    private String sku;

    @Size(max = 255)
    private String variantName;

    /** Optional; when blank the storefront falls back to the product description. */
    private String variantDescription;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00")
    private BigDecimal price;

    @NotNull(message = "Quantity is required")
    @Min(value = 0)
    private Integer quantityAvailable;

    @Min(value = 0)
    private Integer lowStockThreshold = 5;

    /**
     * Archive state. Null means "active" on create and "leave unchanged" on update — an editor
     * saving a price change must not silently re-activate a variant someone archived.
     */
    private Boolean isActive;

    /** Variant-level attributes like frame_color, lens_color */
    private Map<String, String> attributes;
}
