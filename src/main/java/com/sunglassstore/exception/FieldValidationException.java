package com.sunglassstore.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cross-field validation failures the service detects after bean validation has passed — a SKU
 * duplicated between two variants of one request, say. Carries the errors keyed by the same field
 * paths bean validation would use ({@code variants[1].sku}), so the client renders both kinds of
 * failure identically and can point at the exact offending input rather than showing "one or more
 * fields have validation errors".
 */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(Map<String, String> fieldErrors) {
        super("One or more fields have validation errors");
        this.fieldErrors = new LinkedHashMap<>(fieldErrors);
    }

    public FieldValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
