package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 150)
    private String categoryName;

    private String description;

    private Long parentCategoryId;
}
