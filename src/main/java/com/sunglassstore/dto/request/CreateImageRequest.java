package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateImageRequest {

    @NotBlank(message = "Image URL is required")
    @Size(max = 2048)
    @Pattern(regexp = "https?://.+", message = "Image URL must use HTTP or HTTPS")
    private String imageUrl;

    @Size(max = 255)
    private String altText;

    @Min(value = 0, message = "Display order cannot be negative")
    private Integer displayOrder = 0;

    private Boolean isPrimary = false;

    /**
     * The colourway this photograph shows, or null for a general product photo. Validated against
     * the product's own variants — an id belonging to someone else's product is rejected rather
     * than stored, which the database would also refuse now that VARIANT_ID is a foreign key.
     */
    private Long variantId;
}
