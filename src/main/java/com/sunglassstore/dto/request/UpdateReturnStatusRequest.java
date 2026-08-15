package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
public class UpdateReturnStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;

    @Size(max = 2000, message = "Admin comments cannot exceed 2000 characters")
    private String adminComments;

    private Map<Long, String> itemConditions;
}
