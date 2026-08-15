package com.sunglassstore.dto.request;

import com.sunglassstore.entity.enums.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModerateReviewRequest {
    @NotNull(message = "Review status is required")
    private ReviewStatus status;
}
