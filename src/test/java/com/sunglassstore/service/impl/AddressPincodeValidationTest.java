package com.sunglassstore.service.impl;

import com.sunglassstore.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressPincodeValidationTest {

    @Test
    void acceptsAValidIndianPinCode() {
        assertEquals("560001", AddressServiceImpl.normalisePincode("560001", "India"));
    }

    @Test
    void trimsSurroundingWhitespaceRatherThanRejectingIt() {
        assertEquals("560001", AddressServiceImpl.normalisePincode("  560001\t", "India"));
    }

    @Test
    void preservesALeadingZeroForCountriesThatUseOne() {
        // Spanish postal codes are five digits and genuinely start with zero. Coercing the
        // value to a number anywhere in the stack would silently turn this into 8001.
        assertEquals("08001", AddressServiceImpl.normalisePincode("08001", "Spain"));
    }

    @Test
    void rejectsLettersAndSymbols() {
        assertEquals("Pincode must contain digits only.",
                assertThrows(BadRequestException.class,
                        () -> AddressServiceImpl.normalisePincode("560A01", "India")).getMessage());
        assertEquals("Pincode must contain digits only.",
                assertThrows(BadRequestException.class,
                        () -> AddressServiceImpl.normalisePincode("560-001", "India")).getMessage());
    }

    @Test
    void rejectsInteriorWhitespace() {
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode("560 001", "India"));
    }

    @Test
    void rejectsAnIndianPinCodeOfTheWrongLength() {
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode("56001", "India"));
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode("5600012", "India"));
    }

    @Test
    void rejectsAnIndianPinCodeStartingWithZero() {
        // No Indian PIN code begins with 0; the first digit is the postal region, 1-9.
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode("060001", "India"));
    }

    @Test
    void matchesTheCountryCaseInsensitivelyAndIgnoringSurroundingSpace() {
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode("060001", " india "));
    }

    @Test
    void appliesAGenericRangeToOtherCountriesRatherThanGuessingTheirFormat() {
        assertEquals("1234567890", AddressServiceImpl.normalisePincode("1234567890", "Australia"));
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode("12", "Spain"));
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode("12345678901", "Spain"));
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode("", "India"));
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode(null, "India"));
        assertThrows(BadRequestException.class, () -> AddressServiceImpl.normalisePincode("   ", "India"));
    }
}
