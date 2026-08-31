package com.sunglassstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shiprocket API configuration bound from {@code shiprocket.*} properties.
 * <p>
 * Authentication uses the Shiprocket account's login email and password —
 * Shiprocket does not issue separate API keys. The bearer token obtained
 * from {@code /auth/login} is valid for 10 days; the client refreshes it
 * well before expiry.
 */
@ConfigurationProperties(prefix = "shiprocket")
public record ShiprocketConfig(
        String baseUrl,
        String email,
        String password,
        String pickupLocation,
        String webhookSecret
) {}
