package com.sunglassstore.dto.request;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255)
    private String productName;

    /**
     * Optional public URL for the product. Omitted on create, the server derives one from the name;
     * omitted on update, the existing one is KEPT — a rename must not move the product's URL.
     * Supplying a different value is the only way to change it, and is validated against the same
     * rules as a generated slug.
     */
    @Size(max = 160)
    private String slug;

    private String productDescription;

    @Size(max = 150)
    private String brand;

    /**
     * Legacy family-level fallback price. Optional now that every variant carries its own: when
     * omitted, the server records the Main Variant's price so pre-redesign readers keep seeing a
     * sensible number.
     */
    @DecimalMin(value = "0.00", message = "Price must be non-negative")
    private BigDecimal basePrice;

    private Long taxRateId;

    @NotNull(message = "Product category is required")
    @Size(min = 1, max = 1, message = "Select exactly one product category")
    private List<Long> categoryIds;

    /** Key-value pairs for product-level attributes like frame_material, uv_protection etc. */
    private Map<String, String> attributes;

    /**
     * Draft support: false creates (or keeps) the product unpublished, true publishes it. Null
     * means "active" on create — the pre-redesign behaviour — and "leave it alone" on update, so
     * an editor saving field changes cannot accidentally publish a draft.
     */
    private Boolean isActive;

    /**
     * The version the editor loaded, for the read-edit-save conflict check on update. Null skips
     * the check (legacy callers); a stale value is refused with 409 rather than silently
     * overwriting another administrator's save.
     */
    private Long version;

    /**
     * The family's variants in display order: index 0 is the Main Product (position 1). The
     * structured replacement for {@link #initialVariant} — a create needs at least one entry, an
     * update must list every existing variant (removal is a separate, guarded operation).
     */
    @Valid
    private List<CreateVariantRequest> variants;

    /**
     * Pre-redesign single-variant field, kept so existing callers don't break. Equivalent to a
     * one-entry {@link #variants} list; sending both is refused rather than guessed at.
     */
    @Valid
    private CreateVariantRequest initialVariant;
}
