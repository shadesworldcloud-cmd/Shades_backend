package com.sunglassstore.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors sunglass-store-frontend/src/services/phone.test.js case for case. The two files are the
 * contract between client-side feedback and server-side enforcement; if one is changed without the
 * other, one of them starts failing, which is the intended alarm.
 */
class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"6123456789", "7123456789", "8123456789", "9876543210"})
    @DisplayName("every valid leading digit is accepted and stored as E.164")
    void acceptsValidNationalNumbers(String number) {
        assertEquals("+91" + number, PhoneNumbers.normalise(number));
    }

    @ParameterizedTest
    @ValueSource(strings = {"919876543210", "+919876543210", "98765 43210", "98765-43210",
            "+91 98765-43210", "+91-98765-43210"})
    @DisplayName("country-code forms and human formatting all normalise to one value")
    void normalisesAcceptedForms(String written) {
        assertEquals("+919876543210", PhoneNumbers.normalise(written));
    }

    @ParameterizedTest
    @ValueSource(strings = {"987654321", "98765432101", "1", "0123456789", "1234567890", "5123456789"})
    @DisplayName("wrong length or wrong leading digit is rejected")
    void rejectsBadNationalNumbers(String number) {
        assertNull(PhoneNumbers.normalise(number));
    }

    @ParameterizedTest
    @ValueSource(strings = {"98765abcde", "9876543210x12", "+9876543210e5", "9876543210.0",
            "98765 4321O", "(98765) 43210", "98765.43210"})
    @DisplayName("letters, extensions, decimals and unsupported punctuation are rejected")
    void rejectsJunk(String value) {
        assertNull(PhoneNumbers.normalise(value));
    }

    @Test
    @DisplayName("a leading sign is not stripped into validity")
    void rejectsSignedValue() {
        // The trap: removing "-" wherever it appeared would turn this into a valid number.
        assertNull(PhoneNumbers.normalise("-9876543210"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+91919876543210", "9191987654321", "+91+919876543210", "919876543210 91"})
    @DisplayName("a doubled country code is rejected rather than trimmed until it fits")
    void rejectsDoubledPrefix(String value) {
        assertNull(PhoneNumbers.normalise(value));
    }

    @Test
    @DisplayName("+91 followed by a genuine number starting 9 is still valid")
    void acceptsNumberThatLooksLikeADoubledPrefix() {
        // +91 9198765432 is a real ten-digit number beginning 9, not a repeated country code.
        assertEquals("+919198765432", PhoneNumbers.normalise("+919198765432"));
    }

    @Test
    @DisplayName("blank is acceptable because the field is optional, and stores as null")
    void blankIsOptional() {
        assertTrue(PhoneNumbers.isAcceptable(null));
        assertTrue(PhoneNumbers.isAcceptable(""));
        assertTrue(PhoneNumbers.isAcceptable("   "));
        assertNull(PhoneNumbers.toStored("   "));
        assertNull(PhoneNumbers.toStored(null));
    }

    @Test
    @DisplayName("an unacceptable value is not acceptable, so the constraint can reject it")
    void unacceptableIsReported() {
        org.junit.jupiter.api.Assertions.assertFalse(PhoneNumbers.isAcceptable("12345"));
        org.junit.jupiter.api.Assertions.assertFalse(PhoneNumbers.isAcceptable("-9876543210"));
    }
}
