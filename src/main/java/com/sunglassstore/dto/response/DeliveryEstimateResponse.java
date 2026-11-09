package com.sunglassstore.dto.response;

import java.time.LocalDate;

/**
 * Customer-facing delivery estimate for a pincode.
 *
 * @param pincode       the destination pincode that was checked
 * @param serviceable   {@code true}/{@code false} when Shiprocket answered; {@code null} when it could
 *                      not be reached and the estimate is the standard fallback
 * @param earliestDate  earliest expected delivery date (null when not serviceable)
 * @param latestDate    latest expected delivery date (null when not serviceable)
 * @param courierName   the fastest courier partner, when known
 * @param courierCount  how many courier partners can deliver
 * @param source        {@code SHIPROCKET} or {@code FALLBACK}
 */
public record DeliveryEstimateResponse(
        String pincode,
        Boolean serviceable,
        LocalDate earliestDate,
        LocalDate latestDate,
        String courierName,
        int courierCount,
        String source
) {
    public static final String SOURCE_SHIPROCKET = "SHIPROCKET";
    public static final String SOURCE_FALLBACK = "FALLBACK";
}
