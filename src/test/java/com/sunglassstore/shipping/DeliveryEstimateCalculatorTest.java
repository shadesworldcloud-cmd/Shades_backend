package com.sunglassstore.shipping;

import com.sunglassstore.dto.response.DeliveryEstimateResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketServiceabilityResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketServiceabilityResponse.CourierCompany;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryEstimateCalculatorTest {

    /** Tuesday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Test
    void singleDateIsTheRecommendedCourierPlusDispatch() {
        var response = response(2,
                courier(1, "Xpressbees", "Nov 13, 2026", "3"),   // Fri (fastest)
                courier(2, "Delhivery", "Nov 16, 2026", "6"));   // Mon (recommended)

        DeliveryEstimateResponse estimate = DeliveryEstimateCalculator.customerEstimate("560001", response, TODAY, 1);

        assertEquals(Boolean.TRUE, estimate.serviceable());
        assertEquals(LocalDate.of(2026, 11, 17), estimate.earliestDate()); // Mon + 1 working day
        assertEquals(LocalDate.of(2026, 11, 17), estimate.latestDate());
        assertEquals("Delhivery", estimate.courierName());
        assertEquals(2, estimate.courierCount());
        assertEquals(DeliveryEstimateResponse.SOURCE_SHIPROCKET, estimate.source());
    }

    @Test
    void withoutRecommendationTheDateIsTheFastestCourier() {
        var response = response(null,
                courier(2, "Delhivery", "Nov 16, 2026", null),
                courier(1, "Xpressbees", "Nov 13, 2026", null));

        var estimate = DeliveryEstimateCalculator.customerEstimate("560001", response, TODAY, 0);

        assertEquals(LocalDate.of(2026, 11, 13), estimate.earliestDate());
        assertEquals(LocalDate.of(2026, 11, 13), estimate.latestDate());
        assertEquals("Xpressbees", estimate.courierName());
    }

    @Test
    void recommendedCourierWithoutUsableDateFallsBackToFastest() {
        var response = response(9,
                courier(9, "Recommended", "", null),
                courier(1, "A", "Nov 13, 2026", null),
                courier(2, "B", "Nov 12, 2026", null));
        var estimate = DeliveryEstimateCalculator.customerEstimate("560001", response, TODAY, 0);
        assertEquals(LocalDate.of(2026, 11, 12), estimate.earliestDate());
        assertEquals(LocalDate.of(2026, 11, 12), estimate.latestDate());
        assertEquals("B", estimate.courierName());
    }

    @Test
    void recommendedCourierFasterThanOthersIsUsed() {
        var response = response(2, courier(1, "A", "Nov 13, 2026", null), courier(2, "B", "Nov 12, 2026", null));
        var estimate = DeliveryEstimateCalculator.customerEstimate("560001", response, TODAY, 0);
        assertEquals(LocalDate.of(2026, 11, 12), estimate.earliestDate());
        assertEquals(LocalDate.of(2026, 11, 12), estimate.latestDate());
        assertEquals("B", estimate.courierName());
    }

    @Test
    void daysAreUsedWhenEtdIsMissingOrStaleAndSundaysAreSkipped() {
        // Tue + 5 calendar days = Sunday -> Monday 16th; + 1 dispatch working day = Tue 17th
        var missingEtd = response(null, courier(1, "A", "", "5"));
        assertEquals(LocalDate.of(2026, 11, 17),
                DeliveryEstimateCalculator.customerEstimate("1", missingEtd, TODAY, 1).earliestDate());

        var staleEtd = response(null, courier(1, "A", "Nov 01, 2026", "2"));
        assertEquals(LocalDate.of(2026, 11, 12),
                DeliveryEstimateCalculator.customerEstimate("1", staleEtd, TODAY, 0).earliestDate());
    }

    @Test
    void noCouriersMeansNotServiceable() {
        var estimate = DeliveryEstimateCalculator.customerEstimate("999999", response(null), TODAY, 1);
        assertEquals(Boolean.FALSE, estimate.serviceable());
        assertNull(estimate.earliestDate());
        assertNull(estimate.latestDate());

        var nullData = new ShiprocketServiceabilityResponse(404, null);
        assertEquals(Boolean.FALSE, DeliveryEstimateCalculator.customerEstimate("999999", nullData, TODAY, 1).serviceable());
    }

    @Test
    void serviceableButUndatedCouriersGetTheStandardWindow() {
        var estimate = DeliveryEstimateCalculator.customerEstimate("560001",
                response(null, courier(1, "A", null, null)), TODAY, 1);
        assertEquals(Boolean.TRUE, estimate.serviceable());
        assertEquals(DeliveryEstimateResponse.SOURCE_FALLBACK, estimate.source());
        assertEquals(LocalDate.of(2026, 11, 14), estimate.earliestDate());
        assertEquals(LocalDate.of(2026, 11, 17), estimate.latestDate());
    }

    @Test
    void fallbackIsThreeToFiveWorkingDaysAfterDispatch() {
        var fallback = DeliveryEstimateCalculator.fallback("560001", TODAY, 1);
        assertNull(fallback.serviceable());
        assertEquals(DeliveryEstimateResponse.SOURCE_FALLBACK, fallback.source());
        assertEquals(LocalDate.of(2026, 11, 14), fallback.earliestDate()); // Wed dispatch + 3 (Thu, Fri, Sat)
        assertEquals(LocalDate.of(2026, 11, 17), fallback.latestDate());   // + Mon, Tue (Sunday skipped)
    }

    @Test
    void shipmentEtaPrefersAssignedThenRecommendedThenFastest() {
        var response = response(2,
                courier(1, "Xpressbees", "Nov 13, 2026", null),
                courier(2, "Delhivery", "2026-11-16 18:00:00", null),
                courier(3, "BlueDart", "Nov 12, 2026", null));

        assertEquals(Optional.of(LocalDateTime.of(2026, 11, 13, 20, 0)),
                DeliveryEstimateCalculator.shipmentEta(response, 1, TODAY));
        assertEquals(Optional.of(LocalDateTime.of(2026, 11, 16, 18, 0)),
                DeliveryEstimateCalculator.shipmentEta(response, null, TODAY));
        assertEquals(Optional.of(LocalDateTime.of(2026, 11, 16, 18, 0)),
                DeliveryEstimateCalculator.shipmentEta(response, 77, TODAY)); // unknown courier -> recommended

        var noRecommendation = response(null, courier(1, "A", "Nov 13, 2026", null), courier(3, "B", "Nov 12, 2026", null));
        assertEquals(Optional.of(LocalDateTime.of(2026, 11, 12, 20, 0)),
                DeliveryEstimateCalculator.shipmentEta(noRecommendation, null, TODAY));
        assertTrue(DeliveryEstimateCalculator.shipmentEta(response(null), null, TODAY).isEmpty());
        assertTrue(DeliveryEstimateCalculator.shipmentEta(null, null, TODAY).isEmpty());
    }

    private static CourierCompany courier(int id, String name, String etd, String days) {
        return new CourierCompany(id, name, BigDecimal.valueOf(60), etd, days);
    }

    private static ShiprocketServiceabilityResponse response(Integer recommended, CourierCompany... couriers) {
        List<CourierCompany> list = Arrays.asList(couriers);
        return new ShiprocketServiceabilityResponse(200,
                new ShiprocketServiceabilityResponse.ServiceabilityData(list, recommended));
    }
}
