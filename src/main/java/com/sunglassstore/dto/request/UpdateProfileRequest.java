package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import com.sunglassstore.validation.IndianMobile;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;
    // One rule, one message. @Size(max = 20) and a looser @Pattern used to sit here too — three

    // constraints on one field, whichever fired first deciding what the customer read. @IndianMobile

    // rejects everything they would and more, so they are redundant; the profile form having a phone

    // pattern while registration and addresses had none is exactly the drift this consolidates.

    @IndianMobile
    private String phoneNumber;

    /**
     * The version the customer was looking at when they edited. Optional for backwards
     * compatibility; when present the update is refused if the row has moved on.
     */
    private Long version;
}
