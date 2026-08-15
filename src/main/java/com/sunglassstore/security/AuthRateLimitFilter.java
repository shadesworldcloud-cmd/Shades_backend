package com.sunglassstore.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_SECONDS = 60;
    private static final Map<String, Integer> LIMITS = Map.of(
            "/api/auth/login", 20,
            "/api/auth/register", 10,
            "/api/auth/google", 20,
            "/api/auth/refresh", 60,
            "/api/auth/forgot-password", 5,
            "/api/auth/reset-password", 10,
            "/api/auth/verify-email", 10,
            "/api/auth/resend-verification", 5);
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITS.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long now = Instant.now().getEpochSecond();
        String key = request.getRemoteAddr() + ':' + request.getRequestURI();
        Window current = windows.compute(key, (ignored, previous) ->
                previous == null || now - previous.startedAt >= WINDOW_SECONDS
                        ? new Window(now, 1) : new Window(previous.startedAt, previous.count + 1));

        if (requests.incrementAndGet() % 1_000 == 0) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_SECONDS);
        }

        if (current.count > LIMITS.get(request.getRequestURI())) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Please try again later\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private record Window(long startedAt, int count) {}
}
