package com.sunglassstore.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends email via Resend's HTTP API (https://resend.com/docs/api-reference/emails/send-email).
 *
 * Unlike SMTP, this uses HTTPS (port 443), which is not blocked by Render's free tier
 * or other cloud platforms that restrict outbound SMTP traffic.
 */
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend")
@Slf4j
public class ResendEmailService implements EmailService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.email.resend.api-key}")
    private String apiKey;

    @Value("${app.email.resend.from:Shades World <onboarding@resend.dev>}")
    private String fromAddress;

    @Override
    public void send(EmailMessage email) {
        String jsonBody = buildJsonBody(email);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Resend API returned {} for '{}' to {}: {}",
                        response.statusCode(), email.subject(), email.to(), response.body());
                throw new EmailDeliveryException(
                        "Resend API error " + response.statusCode() + ": " + response.body(), null);
            }
        } catch (EmailDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Email delivery failed to {} for '{}': {}", email.to(), email.subject(), e.getMessage());
            throw new EmailDeliveryException("Email delivery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the JSON payload manually to avoid pulling in a JSON library dependency
     * just for this one call. The values are escaped for safe JSON embedding.
     */
    private String buildJsonBody(EmailMessage email) {
        return "{" +
                "\"from\":\"" + escapeJson(fromAddress) + "\"," +
                "\"to\":[\"" + escapeJson(email.to()) + "\"]," +
                "\"subject\":\"" + escapeJson(email.subject()) + "\"," +
                "\"text\":\"" + escapeJson(email.body()) + "\"" +
                "}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
