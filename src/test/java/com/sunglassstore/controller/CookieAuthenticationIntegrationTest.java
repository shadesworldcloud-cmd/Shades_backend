package com.sunglassstore.controller;

import com.sunglassstore.dto.request.LoginRequest;
import com.sunglassstore.dto.request.RefreshTokenRequest;
import com.sunglassstore.dto.response.AuthResponse;
import com.sunglassstore.security.AuthCookieService;
import com.sunglassstore.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CookieAuthenticationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private AuthenticationService authenticationService;

    @Test
    void loginRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginPlacesTokensOnlyInHttpOnlyCookies() throws Exception {
        when(authenticationService.login(any(LoginRequest.class))).thenReturn(auth());

        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value(AuthCookieService.ACCESS_COOKIE, "access-secret"))
                .andExpect(cookie().httpOnly(AuthCookieService.ACCESS_COOKIE, true))
                .andExpect(cookie().path(AuthCookieService.ACCESS_COOKIE, "/"))
                .andExpect(cookie().value(AuthCookieService.REFRESH_COOKIE, "refresh-secret"))
                .andExpect(cookie().httpOnly(AuthCookieService.REFRESH_COOKIE, true))
                .andExpect(cookie().path(AuthCookieService.REFRESH_COOKIE, "/api/auth"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void refreshReadsTokenFromCookieAndRotatesBothCookies() throws Exception {
        when(authenticationService.refresh(any(RefreshTokenRequest.class))).thenReturn(auth());
        MockCookie refresh = new MockCookie(AuthCookieService.REFRESH_COOKIE, "old-refresh");

        mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(cookie().value(AuthCookieService.ACCESS_COOKIE, "access-secret"))
                .andExpect(cookie().value(AuthCookieService.REFRESH_COOKIE, "refresh-secret"));

        verify(authenticationService).refresh(org.mockito.ArgumentMatchers.argThat(
                request -> "old-refresh".equals(request.getRefreshToken())));
    }

    @Test
    void logoutWithExpiredAccessStillRevokesRefreshSessionAndClearsCookies() throws Exception {
        MockCookie refresh = new MockCookie(AuthCookieService.REFRESH_COOKIE, "old-refresh");

        mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AuthCookieService.ACCESS_COOKIE, 0))
                .andExpect(cookie().maxAge(AuthCookieService.REFRESH_COOKIE, 0));

        verify(authenticationService).logoutByRefreshToken("old-refresh");
    }

    private AuthResponse auth() {
        return new AuthResponse("access-secret", "refresh-secret", "Bearer", 7L,
                "user@example.com", "User");
    }
}
