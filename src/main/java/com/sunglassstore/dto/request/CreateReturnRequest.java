package com.sunglassstore.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateReturnRequest {

    @NotNull(message = "Order ID is required")
    @Positive(message = "Order ID must be positive")
    private Long orderId;

    @NotBlank(message = "Return reason is required")
    @Size(max = 255, message = "Return reason cannot exceed 255 characters")
    private String returnReason;

    @Size(max = 2000, message = "Customer comments cannot exceed 2000 characters")
    private String customerComments;

    @NotEmpty(message = "At least one return item is required")
    @Valid
    private List<ReturnItemRequest> items;

    @Getter
    @Setter
    public static class ReturnItemRequest {

        @NotNull(message = "Order item ID is required")
        @Positive(message = "Order item ID must be positive")
        private Long orderItemId;

        @NotNull(message = "Quantity is required")
        @jakarta.validation.constraints.Min(value = 1)
        private Integer quantity;

        @NotBlank(message = "Item condition is required")
        @Size(max = 50, message = "Item condition cannot exceed 50 characters")
        private String itemCondition;

        @Size(max = 255, message = "Item return reason cannot exceed 255 characters")
        private String returnReason;
    }
}
