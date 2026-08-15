package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import com.sunglassstore.validation.IndianMobile;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressRequest {

    private String addressType = "SHIPPING";

    @NotBlank(message = "Recipient name is required")
    @Size(max = 255)
    private String recipientName;
    // One rule, one message. @Size(max = 20) and a looser @Pattern used to sit here too — three

    // constraints on one field, whichever fired first deciding what the customer read. @IndianMobile

    // rejects everything they would and more, so they are redundant; the profile form having a phone

    // pattern while registration and addresses had none is exactly the drift this consolidates.

    @IndianMobile
    private String phoneNumber;

    @Size(max = 50)
    private String houseNumber;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100)
    private String state;

    // Digits only, and kept as a String so a leading zero (e.g. Spanish "08001") survives.
    // The country-specific length rule lives in AddressServiceImpl so its message actually
    // reaches the client: GlobalExceptionHandler buries per-field bean-validation messages in
    // validationErrors and sends a generic "message", which is the only field the frontend reads.
    @NotBlank(message = "Pincode is required")
    @Pattern(regexp = "^[0-9]+$", message = "Pincode must contain digits only")
    @Size(max = 20)
    private String pincode;

    @NotBlank(message = "Country is required")
    @Size(max = 100)
    private String country;

    private Boolean isDefault = false;
}
