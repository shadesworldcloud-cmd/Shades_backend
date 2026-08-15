package com.sunglassstore.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a phone field as an optional Indian mobile number.
 *
 * A bean-validation constraint rather than a service-layer check on purpose: GlobalExceptionHandler
 * returns one message per field in `validationErrors`, and the frontend now renders those beneath
 * the field they belong to — so the message reaches the customer where it is useful. (That was not
 * true before: the client read only the generic top-level `message` and dropped the map.)
 */
@Documented
@Constraint(validatedBy = IndianMobileValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface IndianMobile {
    String message() default "Enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
