package com.sunglassstore.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF disabled: the API is stateless (JWT in HttpOnly cookies) and served
                // cross-origin (Vercel → Render). Cookie-based CSRF tokens cannot work
                // across different domains with SameSite restrictions. CORS origin checks
                // and the HttpOnly JWT cookie provide equivalent protection.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=(), payment=()")))
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                "/api/auth/google", "/api/auth/refresh",
                                "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/api/auth/verify-email", "/api/auth/resend-verification",
                                "/api/auth/csrf", "/api/auth/logout").permitAll()
                        // Admin product reads, gated BEFORE the public GET rule below — order
                        // matters, the first matching rule wins. /api/products/admin/all is a GET,
                        // so the public rule would otherwise permit it at this layer and leave
                        // @PreAuthorize on the method as the only thing standing between a guest
                        // and the full catalogue including unpublished drafts. It does hold (a
                        // guest gets 403), but one annotation being the entire boundary is a
                        // single point of failure: deleting it would silently make drafts public.
                        // Same belt-and-braces treatment as /api/offers/automatic/admin/** below.
                        .requestMatchers("/api/products/admin/**").hasAnyRole("ADMIN", "INVENTORY_MANAGER")
                        // Public product browsing. Reads only — every mutation under /api/products
                        // is a POST/PUT/PATCH/DELETE and falls through to .anyRequest().authenticated()
                        // plus @PreAuthorize on the method.
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/products/*").permitAll()
                        // Images are now served from ImageKit CDN; no local /uploads path needed.
                        // Coupon validation for authenticated users
                        .requestMatchers("/api/coupons/**").authenticated()
                        // The automatic offer has to reach a signed-out visitor: the banner renders
                        // on the storefront before anyone logs in, and a guest bag has no
                        // server-side cart, so its lines are priced from the request body. Both are
                        // reads — they return prices, never accept them. Every mutation lives under
                        // /admin below and is additionally gated by @PreAuthorize on the method.
                        // The hero image is the first thing on the home page, so a signed-out
                        // visitor has to be able to read it. GET only, and only this one path: the
                        // writes live under /api/admin/storefront and are ADMIN-gated there and
                        // again with @PreAuthorize on each method.
                        .requestMatchers(HttpMethod.GET, "/api/storefront/settings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/offers/automatic/active").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/offers/automatic/quote").permitAll()
                        .requestMatchers("/api/offers/automatic/admin/**").hasRole("ADMIN")
                        // Admin endpoints
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPPORT", "INVENTORY_MANAGER")
                        // Swagger/OpenAPI
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/api-docs/**", "/v3/api-docs/**").hasRole("ADMIN")
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
