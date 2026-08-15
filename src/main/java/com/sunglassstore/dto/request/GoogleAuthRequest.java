package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleAuthRequest {

    @NotBlank(message = "Google credential is required")
    @Size(max = 4096, message = "Google credential is too long")
    private String credential;
}
