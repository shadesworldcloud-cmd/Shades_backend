package com.sunglassstore.service;

import com.sunglassstore.config.ShiprocketConfig;
import com.sunglassstore.dto.response.DeliveryEstimateResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketServiceabilityResponse;
import com.sunglassstore.shipping.DeliveryEstimateCalculator;
import com.sunglassstore.shipping.ShiprocketDates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for expected delivery dates, backed by Shiprocket serviceability.
 * <p>
 * Customer estimates (product page, checkout) are cached per pincode for a few hours so browsing
 * does not hammer Shiprocket; shipment-time ETAs are always fetched fresh. Any Shiprocket failure
 * degrades to the standard 3–5 working-day promise instead of an error.
 */
@Service
public class DeliveryEstimateService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEstimateService.class);

    public static final String DEFAULT_PICKUP_PINCODE = "302004";
    public static final int DEFAULT_DISPATCH_DAYS = 1;
    /** Typical boxed sunglasses weight in kg used for serviceability checks. */
    public static final double PACKAGE_WEIGHT_KG = 0.3;
    static final Duration CACHE_TTL = Duration.ofHours(6);
    static final int MAX_CACHE_ENTRIES = 5_000;

    private final ShiprocketClient shiprocketClient;
    private final ShiprocketConfig shiprocketConfig;
    private final Clock clock;
    private final Map<String, CachedEstimate> cache = new ConcurrentHashMap<>();

    private record CachedEstimate(DeliveryEstimateResponse estimate, Instant expiresAt) {}

    @Autowired
    public DeliveryEstimateService(ShiprocketClient shiprocketClient, ShiprocketConfig shiprocketConfig) {
        this(shiprocketClient, shiprocketConfig, Clock.system(ShiprocketDates.STORE_ZONE));
    }

    public DeliveryEstimateService(ShiprocketClient shiprocketClient, ShiprocketConfig shiprocketConfig, Clock clock) {
        this.shiprocketClient = shiprocketClient;
        this.shiprocketConfig = shiprocketConfig;
        this.clock = clock;
    }

    /** Customer-facing estimate for a 6-digit Indian pincode. Never throws for Shiprocket problems. */
    public DeliveryEstimateResponse estimate(String pincode) {
        LocalDate today = LocalDate.now(clock);
        String key = pincode + "|" + today;
        CachedEstimate cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.estimate();

        try {
            ShiprocketServiceabilityResponse response = shiprocketClient.checkServiceability(
                    pickupPincode(), pincode, PACKAGE_WEIGHT_KG, false);
            if (response == null) throw new IllegalStateException("empty serviceability response");
            DeliveryEstimateResponse estimate =
                    DeliveryEstimateCalculator.customerEstimate(pincode, response, today, dispatchDays());
            if (cache.size() >= MAX_CACHE_ENTRIES) cache.clear();
            cache.put(key, new CachedEstimate(estimate, clock.instant().plus(CACHE_TTL)));
            return estimate;
        } catch (Exception e) {
            // Not cached: the next request retries Shiprocket.
            log.warn("Shiprocket delivery estimate failed for pincode {}: {}", pincode, e.getMessage());
            return DeliveryEstimateCalculator.fallback(pincode, today, dispatchDays());
        }
    }

    /**
     * Fresh ETA for a shipment going out now. With a courier id (after AWB assignment) that courier's
     * own ETA is used. Empty when Shiprocket cannot provide one; never throws.
     */
    public Optional<LocalDateTime> shipmentEta(String pincode, Integer courierCompanyId) {
        if (pincode == null || pincode.isBlank()) return Optional.empty();
        try {
            ShiprocketServiceabilityResponse response = shiprocketClient.checkServiceability(
                    pickupPincode(), pincode.trim(), PACKAGE_WEIGHT_KG, false);
            return DeliveryEstimateCalculator.shipmentEta(response, courierCompanyId, LocalDate.now(clock));
        } catch (Exception e) {
            log.warn("Could not fetch shipment ETA from Shiprocket for pincode {}: {}", pincode, e.getMessage());
            return Optional.empty();
        }
    }

    public String pickupPincode() {
        String configured = shiprocketConfig == null ? null : shiprocketConfig.pickupPincode();
        return configured == null || configured.isBlank() ? DEFAULT_PICKUP_PINCODE : configured.trim();
    }

    int dispatchDays() {
        Integer configured = shiprocketConfig == null ? null : shiprocketConfig.dispatchDays();
        return configured == null || configured < 0 ? DEFAULT_DISPATCH_DAYS : configured;
    }

    void clearCache() {
        cache.clear();
    }
}
