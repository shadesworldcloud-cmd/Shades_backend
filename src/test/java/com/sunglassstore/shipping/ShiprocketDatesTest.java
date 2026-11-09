package com.sunglassstore.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShiprocketDatesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-11-14 18:30:00   | 2026-11-14T18:30",
            "2026-11-14 18:30      | 2026-11-14T18:30",
            "2026-11-14T18:30:00   | 2026-11-14T18:30",
            "14-11-2026 09:15:00   | 2026-11-14T09:15",
            "Nov 14, 2026 18:00:00 | 2026-11-14T18:00",
            "2026-11-14T13:00:00Z  | 2026-11-14T18:30",
    })
    void parsesShiprocketTimestamps(String etd, String expected) {
        assertEquals(Optional.of(LocalDateTime.parse(expected)), ShiprocketDates.parseDateTime(etd));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nov 14, 2026", "nov 14, 2026", "November 14, 2026", "14 Nov 2026",
            "2026-11-14", "14-11-2026", "14/11/2026", "  Nov  14,  2026 "})
    void dateOnlyValuesLandAtEndOfDay(String etd) {
        assertEquals(Optional.of(LocalDate.of(2026, 11, 14).atTime(20, 0)), ShiprocketDates.parseDateTime(etd));
        assertEquals(Optional.of(LocalDate.of(2026, 11, 14)), ShiprocketDates.parseDate(etd));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "N/A", "soon", "2026-13-40", "0000-00-00 00:00:00"})
    void unusableValuesAreEmptyNotErrors(String etd) {
        assertTrue(ShiprocketDates.parseDateTime(etd).isEmpty());
    }

    @Test
    void parsesEstimatedDeliveryDays() {
        assertEquals(Optional.of(3), ShiprocketDates.parseDays("3"));
        assertEquals(Optional.of(4), ShiprocketDates.parseDays(" 3.2 "));
        assertTrue(ShiprocketDates.parseDays("0").isEmpty());
        assertTrue(ShiprocketDates.parseDays("").isEmpty());
        assertTrue(ShiprocketDates.parseDays(null).isEmpty());
        assertTrue(ShiprocketDates.parseDays("three").isEmpty());
    }

    @Test
    void workingDaysSkipSundays() {
        LocalDate friday = LocalDate.of(2026, 11, 13);
        assertEquals(LocalDate.of(2026, 11, 14), ShiprocketDates.addWorkingDays(friday, 1)); // Sat
        assertEquals(LocalDate.of(2026, 11, 16), ShiprocketDates.addWorkingDays(friday, 2)); // Mon
        assertEquals(friday, ShiprocketDates.addWorkingDays(friday, 0));
        LocalDate sunday = LocalDate.of(2026, 11, 15);
        assertEquals(LocalDate.of(2026, 11, 16), ShiprocketDates.addWorkingDays(sunday, 0));
        assertEquals(LocalDate.of(2026, 11, 16), ShiprocketDates.nextWorkingDay(sunday));
    }
}
