package com.sunglassstore.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthRateLimitFilterTest {
    @Test
    void forgotPasswordIsRateLimitedPerRemoteAddress() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        for (int attempt = 1; attempt <= 6; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
            request.setRemoteAddr("192.0.2.44");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(attempt <= 5 ? 200 : 429, response.getStatus());
            if (attempt == 6) assertEquals("60", response.getHeader("Retry-After"));
        }
    }
}
