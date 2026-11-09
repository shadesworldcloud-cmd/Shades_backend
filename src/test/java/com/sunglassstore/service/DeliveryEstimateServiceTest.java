package com.sunglassstore.service;

import com.sunglassstore.config.ShiprocketConfig;
import com.sunglassstore.dto.response.DeliveryEstimateResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketServiceabilityResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketServiceabilityResponse.CourierCompany;
import com.sunglassstore.shipping.ShiprocketDates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryEstimateServiceTest {

    private ShiprocketClient client;
    private ShiprocketConfig config;
    private MutableClock clock;
    private DeliveryEstimateService service;

    @BeforeEach
    void setUp() {
        client = mock(ShiprocketClient.class);
        config = new ShiprocketConfig("https://example.test", "e", "p", "Primary", "", "302004", 1);
        // 10:00 IST on Tuesday 10 Nov 2026
        clock = new MutableClock(LocalDateTime.of(2026, 11, 10, 10, 0).atZone(ShiprocketDates.STORE_ZONE).toInstant());
        service = new DeliveryEstimateService(client, config, clock);
    }

    @Test
    void estimateComesFromShiprocketAndIsCachedPerPincode() {
        when(client.checkServiceability("302004", "560001", 0.3, false)).thenReturn(serviceable("Nov 13, 2026"));

        DeliveryEstimateResponse first = service.estimate("560001");
        DeliveryEstimateResponse second = service.estimate("560001");

        assertEquals(LocalDate.of(2026, 11, 14), first.earliestDate());
        assertSame(first, second);
        verify(client, times(1)).checkServiceability(any(), any(), anyDouble(), anyBoolean());
    }

    @Test
    void cacheExpiresAfterTtl() {
        when(client.checkServiceability(any(), eq("560001"), anyDouble(), anyBoolean())).thenReturn(serviceable("Nov 13, 2026"));
        service.estimate("560001");
        clock.advanceHours(7);
        service.estimate("560001");
        verify(client, times(2)).checkServiceability(any(), any(), anyDouble(), anyBoolean());
    }

    @Test
    void shiprocketFailureFallsBackAndIsNotCached() {
        when(client.checkServiceability(any(), any(), anyDouble(), anyBoolean()))
                .thenThrow(new RuntimeException("Shiprocket down"));

        DeliveryEstimateResponse estimate = service.estimate("560001");
        service.estimate("560001");

        assertNull(estimate.serviceable());
        assertEquals(DeliveryEstimateResponse.SOURCE_FALLBACK, estimate.source());
        assertEquals(LocalDate.of(2026, 11, 14), estimate.earliestDate());
        assertEquals(LocalDate.of(2026, 11, 17), estimate.latestDate());
        verify(client, times(2)).checkServiceability(any(), any(), anyDouble(), anyBoolean());
    }

    @Test
    void nullResponseFallsBack() {
        when(client.checkServiceability(any(), any(), anyDouble(), anyBoolean())).thenReturn(null);
        assertEquals(DeliveryEstimateResponse.SOURCE_FALLBACK, service.estimate("560001").source());
    }

    @Test
    void unserviceablePincodeIsReported() {
        when(client.checkServiceability(any(), any(), anyDouble(), anyBoolean())).thenReturn(
                new ShiprocketServiceabilityResponse(200, new ShiprocketServiceabilityResponse.ServiceabilityData(List.of(), null)));
        assertEquals(Boolean.FALSE, service.estimate("194101").serviceable());
    }

    @Test
    void pickupPincodeAndDispatchDaysComeFromConfigWithSafeDefaults() {
        var custom = new DeliveryEstimateService(client,
                new ShiprocketConfig("u", "e", "p", "Primary", "", " 110001 ", 0), clock);
        when(client.checkServiceability(eq("110001"), eq("560001"), anyDouble(), anyBoolean()))
                .thenReturn(serviceable("Nov 13, 2026"));
        assertEquals(LocalDate.of(2026, 11, 13), custom.estimate("560001").earliestDate());

        var defaults = new DeliveryEstimateService(client,
                new ShiprocketConfig("u", "e", "p", "Primary", "", null, null), clock);
        assertEquals(DeliveryEstimateService.DEFAULT_PICKUP_PINCODE, defaults.pickupPincode());
        assertEquals(DeliveryEstimateService.DEFAULT_DISPATCH_DAYS, defaults.dispatchDays());
    }

    @Test
    void shipmentEtaIsFreshAndUsesTheAssignedCourier() {
        var response = new ShiprocketServiceabilityResponse(200, new ShiprocketServiceabilityResponse.ServiceabilityData(List.of(
                new CourierCompany(1, "Fast", BigDecimal.ONE, "Nov 12, 2026", null),
                new CourierCompany(24, "Assigned", BigDecimal.ONE, "Nov 16, 2026", null)), null));
        when(client.checkServiceability(any(), eq("560001"), anyDouble(), anyBoolean())).thenReturn(response);

        assertEquals(Optional.of(LocalDateTime.of(2026, 11, 16, 20, 0)), service.shipmentEta("560001", 24));
        assertEquals(Optional.of(LocalDateTime.of(2026, 11, 12, 20, 0)), service.shipmentEta("560001", null));
        verify(client, times(2)).checkServiceability(any(), any(), anyDouble(), anyBoolean());
    }

    @Test
    void shipmentEtaNeverThrows() {
        when(client.checkServiceability(any(), any(), anyDouble(), anyBoolean())).thenThrow(new RuntimeException("x"));
        assertTrue(service.shipmentEta("560001", 24).isEmpty());
        assertTrue(service.shipmentEta(null, 24).isEmpty());
        assertTrue(service.shipmentEta(" ", 24).isEmpty());
    }

    private static ShiprocketServiceabilityResponse serviceable(String etd) {
        return new ShiprocketServiceabilityResponse(200, new ShiprocketServiceabilityResponse.ServiceabilityData(
                List.of(new CourierCompany(1, "Xpressbees", BigDecimal.valueOf(60), etd, "3")), null));
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advanceHours(long hours) { now = now.plusSeconds(hours * 3600); }
        @Override public java.time.ZoneId getZone() { return ShiprocketDates.STORE_ZONE; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
