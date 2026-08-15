package com.sunglassstore.validation;

import java.util.regex.Pattern;

/**
 * The one definition of an acceptable phone number and its canonical storage form.
 *
 * Deliberately mirrors {@code sunglass-store-frontend/src/services/phone.js} rule for rule, the
 * same way AddressServiceImpl.normalisePincode mirrors pincode.js. The frontend copy exists to give
 * immediate feedback; this copy is the one that actually decides, because a client can be bypassed.
 *
 * Scope is Indian mobile numbers: exactly ten national digits beginning 6, 7, 8 or 9. Every flow
 * that collects a number here is collecting a delivery contact, so landlines and short codes are
 * out of scope rather than silently accepted.
 *
 * Storage is E.164 — {@code +91XXXXXXXXXX} — so one person's number has one representation
 * regardless of how they typed it, and "9876543210" and "+91 98765 43210" cannot become two
 * different customers.
 */
public final class PhoneNumbers {

    /** The user-facing message. Kept identical to PHONE_MESSAGE in phone.js. */
    public static final String MESSAGE =
            "Enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9.";

    /**
     * The only shapes whose separators may be stripped: an optional leading "+", then digits, with
     * runs of spaces or hyphens permitted only BETWEEN digits.
     *
     * "Between digits" is load-bearing. Removing "-" wherever it appeared would turn the signed
     * value "-9876543210" into a valid number — an invalid input made valid by its own sanitising.
     * A leading or trailing separator fails this pattern and is never stripped.
     */
    private static final Pattern SHAPE = Pattern.compile("^\\+?[0-9]+(?:[ -]+[0-9]+)*$");
    private static final Pattern NATIONAL = Pattern.compile("^[6-9][0-9]{9}$");

    private PhoneNumbers() {
    }

    /**
     * @return the canonical {@code +91XXXXXXXXXX} form, or null when the value is not acceptable.
     *         A null or blank input returns null, which callers treat as "no number given" —
     *         phone is optional in every flow that collects it.
     */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || !SHAPE.matcher(trimmed).matches()) {
            return null;
        }
        boolean hadPlus = trimmed.startsWith("+");
        String digits = (hadPlus ? trimmed.substring(1) : trimmed).replaceAll("[ -]", "");

        String national = digits;
        if (digits.length() == 12 && digits.startsWith("91")) {
            national = digits.substring(2);
        } else if (hadPlus) {
            // A leading "+" states that a country code follows. Anything that is not exactly
            // 91 + ten digits is a different country or a doubled prefix, not something to guess at.
            return null;
        }
        return NATIONAL.matcher(national).matches() ? "+91" + national : null;
    }

    /** Blank is acceptable (the field is optional); anything present must normalise. */
    public static boolean isAcceptable(String raw) {
        return raw == null || raw.trim().isEmpty() || normalise(raw) != null;
    }

    /** Normalises for storage, mapping "not given" to null so the column holds no empty strings. */
    public static String toStored(String raw) {
        return raw == null || raw.trim().isEmpty() ? null : normalise(raw);
    }
}
