package com.sunglassstore.dto.request;

import jakarta.validation.constraints.Email;
import com.sunglassstore.validation.IndianMobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
    // One rule, one message. @Size(max = 20) and a looser @Pattern used to sit here too — three

    // constraints on one field, whichever fired first deciding what the customer read. @IndianMobile

    // rejects everything they would and more, so they are redundant; the profile form having a phone

    // pattern while registration and addresses had none is exactly the drift this consolidates.

    @IndianMobile
    private String phoneNumber;
}
