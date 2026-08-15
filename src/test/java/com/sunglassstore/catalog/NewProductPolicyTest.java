package com.sunglassstore.catalog;

import com.sunglassstore.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for the New-badge rule.
 *
 * These exist because the boundary is the one part of the rule that cannot be checked by looking at
 * it: the E2E suite proves it end to end by moving PUBLISHED_AT in the database, but that costs a
 * running stack and a real HTTP round trip per case. The isNew(product, Instant) overload takes the
 * clock precisely so the boundary can be pinned to the second here instead.
 *
 * The window is fixed at 30 days in most cases below and varied explicitly in the last one, so a
 * change to the application default cannot quietly invalidate these.
 */
class NewProductPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");

    private static Product published(Instant publishedAt, boolean active) {
        Product product = new Product();
        product.setIsActive(active);
        product.setPublishedAt(publishedAt);
        return product;
    }

    private static NewProductPolicy policy(int days) {
        return new NewProductPolicy(days);
    }

    @Test
    @DisplayName("a product published moments ago is New")
    void freshlyPublished() {
        assertTrue(policy(30).isNew(published(NOW.minusSeconds(5), true), NOW));
    }

    @Test
    @DisplayName("the boundary is exclusive: one second inside is New, exactly 30 days is not")
    void boundaryIsExclusive() {
        Duration window = Duration.ofDays(30);
        assertTrue(policy(30).isNew(published(NOW.minus(window).plusSeconds(1), true), NOW),
                "29d 23h 59m 59s must still be New");
        assertFalse(policy(30).isNew(published(NOW.minus(window), true), NOW),
                "exactly 30 days old must not be New — the bound is exclusive");
        assertFalse(policy(30).isNew(published(NOW.minus(window).minusSeconds(1), true), NOW),
                "30d 0h 0m 1s must not be New");
    }

    @Test
    @DisplayName("an unpublished product is never New, however active it is")
    void neverPublished() {
        assertFalse(policy(30).isNew(published(null, true), NOW),
                "PUBLISHED_AT is null until first activation, so a draft has no age to measure");
    }

    @Test
    @DisplayName("a deactivated product is not New even inside the window")
    void inactiveIsNotNew() {
        assertFalse(policy(30).isNew(published(NOW.minusSeconds(5), false), NOW),
                "a delisted product must not carry a publicly visible badge");
    }

    @Test
    @DisplayName("a null product is not New rather than an exception")
    void nullProduct() {
        assertFalse(policy(30).isNew(null, NOW));
    }

    @Test
    @DisplayName("the window is configuration, not a constant")
    void windowIsConfigurable() {
        Product tenDaysOld = published(NOW.minus(Duration.ofDays(10)), true);
        assertTrue(policy(30).isNew(tenDaysOld, NOW), "inside a 30-day window");
        assertFalse(policy(7).isNew(tenDaysOld, NOW), "outside a 7-day window");
    }

    @Test
    @DisplayName("the answer does not depend on the JVM's default timezone")
    void independentOfDefaultZone() {
        // The bug this guards: publishedAt used to be a LocalDateTime, which round-tripped through
        // the JDBC driver shifted into the JVM zone and moved the boundary by that offset. Instant
        // has no zone, so changing the default must not change a single verdict.
        Product justOutside = published(NOW.minus(Duration.ofDays(30)).minusSeconds(1), true);
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            for (String zone : new String[]{"UTC", "Asia/Kolkata", "Pacific/Kiritimati", "Pacific/Midway"}) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
                assertFalse(policy(30).isNew(justOutside, NOW), "must stay false in " + zone);
            }
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }
}
