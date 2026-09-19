package com.sunglassstore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isBlank()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Requested-With", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        // PayU callbacks are browser-redirect form POSTs originating from PayU's domain,
        // not AJAX calls from our frontend. They carry an Origin header (e.g. https://test.payu.in
        // or https://secure.payu.in) that is never in our allowed-origins list. Blocking them on
        // CORS is wrong — they are secured by PayU's hash, not by origin. A permissive CORS rule
        // on these three paths lets the POST through; the hash check in PayUController is the
        // real gate.
        CorsConfiguration payuCallbackConfig = new CorsConfiguration();
        payuCallbackConfig.setAllowedOrigins(List.of("*"));
        payuCallbackConfig.setAllowedMethods(List.of("POST"));
        payuCallbackConfig.setAllowedHeaders(List.of("*"));
        // allowCredentials must be false when allowedOrigins contains "*"
        payuCallbackConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/payments/payu/success", payuCallbackConfig);
        source.registerCorsConfiguration("/api/payments/payu/failure", payuCallbackConfig);
        source.registerCorsConfiguration("/api/payments/payu/webhook", payuCallbackConfig);
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
