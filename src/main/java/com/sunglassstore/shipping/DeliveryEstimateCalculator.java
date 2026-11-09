package com.sunglassstore.shipping;

import com.sunglassstore.dto.response.DeliveryEstimateResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketServiceabilityResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketServiceabilityResponse.CourierCompany;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure, framework-free logic that turns a Shiprocket serviceability response into dates.
 * Kept separate from the Spring service so it can be tested without HTTP or a clock.
 */
public final class DeliveryEstimateCalculator {

    /** Standard delivery promise used when Shiprocket cannot be reached (matches the shipping policy). */
    public static final int FALLBACK_MIN_WORKING_DAYS = 3;
    public static final int FALLBACK_MAX_WORKING_DAYS = 5;

    private DeliveryEstimateCalculator() {}

    /** One courier's delivery date, resolved from {@code etd} or {@code estimated_delivery_days}. */
    public record CourierEta(int courierCompanyId, String courierName, LocalDateTime eta) {}

    /**
     * Resolves a courier's ETA. Shiprocket's {@code etd} is preferred because it already accounts for
     * the courier's own calendar; {@code estimated_delivery_days} (calendar days from pickup) is the
     * fallback.
     */
    public static Optional<CourierEta> etaOf(CourierCompany courier, LocalDate today) {
        if (courier == null) return Optional.empty();
        Optional<LocalDateTime> eta = ShiprocketDates.parseDateTime(courier.etd())
                .filter(value -> !value.toLocalDate().isBefore(today));
        if (eta.isEmpty()) {
            eta = ShiprocketDates.parseDays(courier.estimatedDeliveryDays())
                    .map(days -> ShiprocketDates.nextWorkingDay(today.plusDays(days))
                            .atTime(ShiprocketDates.DATE_ONLY_ETA_TIME));
        }
        return eta.map(value -> new CourierEta(courier.courierCompanyId(), courier.courierName(), value));
    }

    public static List<CourierEta> etas(ShiprocketServiceabilityResponse response, LocalDate today) {
        return couriers(response).stream()
                .map(courier -> etaOf(courier, today))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(CourierEta::eta))
                .toList();
    }

    /** True when Shiprocket answered and lists at least one courier for the pincode. */
    public static boolean isServiceable(ShiprocketServiceabilityResponse response) {
        return !couriers(response).isEmpty();
    }

    /**
     * The ETA for a shipment that is being handed to Shiprocket now. When the courier is known
     * (after AWB assignment) its own ETA is used; otherwise Shiprocket's recommended courier,
     * then the fastest one.
     */
    public static Optional<LocalDateTime> shipmentEta(ShiprocketServiceabilityResponse response,
                                                      Integer courierCompanyId, LocalDate today) {
        List<CourierEta> etas = etas(response, today);
        if (etas.isEmpty()) return Optional.empty();
        if (courierCompanyId != null) {
            Optional<CourierEta> assigned = etas.stream()
                    .filter(eta -> eta.courierCompanyId() == courierCompanyId).findFirst();
            if (assigned.isPresent()) return assigned.map(CourierEta::eta);
        }
        Integer recommended = recommendedCourierId(response);
        if (recommended != null) {
            Optional<CourierEta> pick = etas.stream()
                    .filter(eta -> eta.courierCompanyId() == recommended).findFirst();
            if (pick.isPresent()) return pick.map(CourierEta::eta);
        }
        return Optional.of(etas.get(0).eta());
    }

    /**
     * The customer-facing range shown before purchase. The earliest date is the fastest courier;
     * the latest is Shiprocket's recommended courier (which auto-assignment usually picks) or the
     * earliest when there is no recommendation. Both are pushed out by the store's dispatch time,
     * because Shiprocket's dates assume a pickup today.
     */
    public static DeliveryEstimateResponse customerEstimate(String pincode,
                                                            ShiprocketServiceabilityResponse response,
                                                            LocalDate today, int dispatchDays) {
        List<CourierEta> etas = etas(response, today);
        if (!isServiceable(response)) {
            return new DeliveryEstimateResponse(pincode, false, null, null, null, 0,
                    DeliveryEstimateResponse.SOURCE_SHIPROCKET);
        }
        int courierCount = couriers(response).size();
        if (etas.isEmpty()) {
            // Serviceable, but no courier gave a usable date: promise the standard window.
            DeliveryEstimateResponse fallback = fallback(pincode, today, dispatchDays);
            return new DeliveryEstimateResponse(pincode, true, fallback.earliestDate(), fallback.latestDate(),
                    null, courierCount, DeliveryEstimateResponse.SOURCE_FALLBACK);
        }
        CourierEta fastest = etas.get(0);
        LocalDate earliest = fastest.eta().toLocalDate();
        LocalDate latest = shipmentEta(response, null, today).map(LocalDateTime::toLocalDate).orElse(earliest);
        if (latest.isBefore(earliest)) latest = earliest;
        return new DeliveryEstimateResponse(pincode, true,
                ShiprocketDates.addWorkingDays(earliest, dispatchDays),
                ShiprocketDates.addWorkingDays(latest, dispatchDays),
                fastest.courierName(), courierCount, DeliveryEstimateResponse.SOURCE_SHIPROCKET);
    }

    /** Standard 3–5 working-day window after dispatch, used when Shiprocket is unavailable. */
    public static DeliveryEstimateResponse fallback(String pincode, LocalDate today, int dispatchDays) {
        LocalDate dispatched = ShiprocketDates.addWorkingDays(today, dispatchDays);
        return new DeliveryEstimateResponse(pincode, null,
                ShiprocketDates.addWorkingDays(dispatched, FALLBACK_MIN_WORKING_DAYS),
                ShiprocketDates.addWorkingDays(dispatched, FALLBACK_MAX_WORKING_DAYS),
                null, 0, DeliveryEstimateResponse.SOURCE_FALLBACK);
    }

    private static Integer recommendedCourierId(ShiprocketServiceabilityResponse response) {
        return response == null || response.data() == null ? null : response.data().recommendedCourierCompanyId();
    }

    private static List<CourierCompany> couriers(ShiprocketServiceabilityResponse response) {
        if (response == null || response.data() == null || response.data().availableCourierCompanies() == null) {
            return List.of();
        }
        return response.data().availableCourierCompanies().stream().filter(Objects::nonNull).toList();
    }
}
