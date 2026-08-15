package com.sunglassstore.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Delegates to PhoneNumbers so the rule has exactly one definition on this side. */
public class IndianMobileValidator implements ConstraintValidator<IndianMobile, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Blank passes: the field is optional everywhere it is collected, and @NotBlank is the
        // right tool where a number is genuinely required.
        return PhoneNumbers.isAcceptable(value);
    }
}
