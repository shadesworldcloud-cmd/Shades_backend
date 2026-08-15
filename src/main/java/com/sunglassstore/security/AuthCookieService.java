package com.sunglassstore.security;

import com.sunglassstore.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthCookieService {
    public static final String ACCESS_COOKIE = "SW_ACCESS";
    public static final String REFRESH_COOKIE = "SW_REFRESH";

    private final boolean secure;
    private final Duration accessLifetime;
    private final Duration refreshLifetime;

    public AuthCookieService(@Value("${security.cookie.secure:false}") boolean secure,
                             @Value("${security.jwt.access-token-expiration}") long accessMillis,
                             @Value("${security.jwt.refresh-token-expiration}") long refreshMillis) {
        this.secure = secure;
        this.accessLifetime = Duration.ofMillis(accessMillis);
        this.refreshLifetime = Duration.ofMillis(refreshMillis);
    }

    public void write(HttpServletResponse response, AuthResponse auth) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, auth.getAccessToken(), "/", accessLifetime).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, auth.getRefreshToken(), "/api/auth", refreshLifetime).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", "/", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", "/api/auth", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        // SameSite=None is required for cross-origin cookies (Vercel frontend → Render backend).
        // When SameSite=None, Secure must be true (browsers enforce this).
        // In local dev (secure=false), fall back to Lax so cookies still work on localhost.
        String sameSite = secure ? "None" : "Lax";
        return ResponseCookie.from(name, value)
                .httpOnly(true).secure(secure).sameSite(sameSite).path(path).maxAge(maxAge).build();
    }
}
