package com.sunglassstore.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A cart to be priced by the server.
 *
 * Quantities and variant ids only — deliberately no prices and no discount. The browser says what
 * is in the bag; the server decides what it costs. A guest has no server-side cart, so the lines
 * come from the request for everyone, and a signed-in shopper's request is cross-checked against
 * their stored cart nowhere in this DTO precisely because the answer is priced from the database
 * either way: nothing a caller could lie about here is used as money.
 */
@Getter
@Setter
public class OfferQuoteRequest {

    @Valid
    @Size(max = 200, message = "A cart cannot contain more than 200 distinct lines")
    private List<QuoteLine> lines;

    @Getter
    @Setter
    public static class QuoteLine {
        @NotNull(message = "variantId is required")
        private Long variantId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;
    }
}
