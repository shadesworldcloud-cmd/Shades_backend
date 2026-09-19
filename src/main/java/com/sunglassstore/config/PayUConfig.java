package com.sunglassstore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Holds PayU merchant credentials and endpoint configuration.
 * Values are read from application.properties / environment variables prefixed with "payu.".
 */
@Configuration
@ConfigurationProperties(prefix = "payu")
@Getter
@Setter
public class PayUConfig {

    private String merchantKey;
    private String merchantSalt;
    /** Base URL without trailing slash, e.g. https://test.payu.in */
    private String baseUrl;
    /** Frontend URL where PayU redirects after a successful payment */
    private String successUrl;
    /** Frontend URL where PayU redirects after a failed payment */
    private String failureUrl;
    /** Backend webhook URL for PayU server-to-server callbacks */
    private String webhookUrl;
}
