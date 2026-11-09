package com.sunglassstore.shipping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Date helpers for Shiprocket's {@code etd} (estimated time of delivery) fields.
 * <p>
 * Shiprocket is not consistent about the format: the serviceability API returns values such as
 * {@code "Nov 14, 2026"}, while tracking responses and webhooks send {@code "2026-11-14 18:00:00"}.
 * Every known variant is accepted here so callers never have to care. Unparseable or blank values
 * yield {@link Optional#empty()} rather than an exception — a missing estimate must never break
 * checkout, shipment creation or webhook processing.
 */
public final class ShiprocketDates {

    /** Shiprocket and the store both operate in India. */
    public static final ZoneId STORE_ZONE = ZoneId.of("Asia/Kolkata");

    /** Time attached to a date-only estimate: "by end of the delivery day". */
    public static final LocalTime DATE_ONLY_ETA_TIME = LocalTime.of(20, 0);

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            pattern("yyyy-MM-dd HH:mm:ss"),
            pattern("yyyy-MM-dd HH:mm"),
            pattern("yyyy-MM-dd'T'HH:mm:ss"),
            pattern("yyyy-MM-dd'T'HH:mm"),
            pattern("dd-MM-yyyy HH:mm:ss"),
            pattern("dd-MM-yyyy HH:mm"),
            pattern("MMM d, yyyy HH:mm:ss"),
            pattern("MMM d, yyyy HH:mm"),
            pattern("MMM d, yyyy h:mm a"));

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            pattern("yyyy-MM-dd"),
            pattern("MMM d, yyyy"),
            pattern("MMMM d, yyyy"),
            pattern("d MMM yyyy"),
            pattern("d MMMM yyyy"),
            pattern("dd-MM-yyyy"),
            pattern("dd/MM/yyyy"));

    private ShiprocketDates() {}

    private static DateTimeFormatter pattern(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                .toFormatter(Locale.ENGLISH);
    }

    /**
     * Parses an etd value into a timestamp. Date-only values are placed at
     * {@link #DATE_ONLY_ETA_TIME} on that day.
     */
    public static Optional<LocalDateTime> parseDateTime(String value) {
        if (value == null) return Optional.empty();
        String text = value.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return Optional.empty();
        // ISO timestamps with fractional seconds or an offset ("2026-11-14T18:00:00.000Z")
        if (text.length() > 19 && text.charAt(10) == 'T') {
            try {
                return Optional.of(java.time.OffsetDateTime.parse(text)
                        .atZoneSameInstant(STORE_ZONE).toLocalDateTime());
            } catch (DateTimeParseException ignored) {
                try { return Optional.of(LocalDateTime.parse(text)); }
                catch (DateTimeParseException ignoredToo) { /* fall through */ }
            }
        }
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try { return Optional.of(LocalDateTime.parse(text, format)); }
            catch (DateTimeParseException ignored) { /* try next */ }
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try { return Optional.of(LocalDate.parse(text, format).atTime(DATE_ONLY_ETA_TIME)); }
            catch (DateTimeParseException ignored) { /* try next */ }
        }
        return Optional.empty();
    }

    /** Parses an etd value and keeps only the calendar date. */
    public static Optional<LocalDate> parseDate(String value) {
        return parseDateTime(value).map(LocalDateTime::toLocalDate);
    }

    /** Parses Shiprocket's {@code estimated_delivery_days}, which arrives as a number or a string. */
    public static Optional<Integer> parseDays(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            int days = (int) Math.ceil(Double.parseDouble(value.trim()));
            return days > 0 ? Optional.of(days) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Adds working days for the store's dispatch, skipping Sundays (Indian couriers and the
     * store's packing team work Monday to Saturday). Zero days leaves the date unchanged unless
     * it is a Sunday, which moves to Monday.
     */
    public static LocalDate addWorkingDays(LocalDate start, int days) {
        LocalDate result = start;
        int remaining = Math.max(0, days);
        while (remaining > 0) {
            result = result.plusDays(1);
            if (result.getDayOfWeek() != DayOfWeek.SUNDAY) remaining--;
        }
        return nextWorkingDay(result);
    }

    /** Couriers do not deliver on Sundays: an estimate landing on one moves to Monday. */
    public static LocalDate nextWorkingDay(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY ? date.plusDays(1) : date;
    }
}
