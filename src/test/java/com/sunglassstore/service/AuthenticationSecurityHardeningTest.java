package com.sunglassstore.service;

import com.sunglassstore.dto.request.LoginRequest;
import com.sunglassstore.dto.request.RefreshTokenRequest;
import com.sunglassstore.entity.RefreshToken;
import com.sunglassstore.entity.User;
import com.sunglassstore.exception.UnauthorizedException;
import com.sunglassstore.repository.*;
import com.sunglassstore.security.JwtService;
import com.sunglassstore.service.impl.AuthenticationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthenticationSecurityHardeningTest {
    private UserRepository users;
    private RefreshTokenRepository refreshTokens;
    private LoginAttemptRepository loginAttempts;
    private AuthenticationManager authenticationManager;
    private AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class); refreshTokens = mock(RefreshTokenRepository.class);
        loginAttempts = mock(LoginAttemptRepository.class); authenticationManager = mock(AuthenticationManager.class);
        service = new AuthenticationServiceImpl(users, mock(RoleRepository.class), refreshTokens, loginAttempts,
                mock(PasswordEncoder.class), authenticationManager, mock(JwtService.class),
                mock(EmailVerificationService.class));
    }

    @Test
    void repeatedRecentFailuresThrottleBeforePasswordVerification() {
        when(loginAttempts.countByEmailIgnoreCaseAndIsSuccessfulFalseAndAttemptedAtAfter(eq("user@example.com"), any()))
                .thenReturn(5L);
        LoginRequest request = new LoginRequest(); request.setEmail(" User@Example.com "); request.setPassword("password");
        assertThrows(UnauthorizedException.class, () -> service.login(request));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void deactivatedUserCannotRotateRefreshToken() {
        User user = user(); user.setIsActive(false);
        RefreshToken token = token(user);
        when(refreshTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
        RefreshTokenRequest request = new RefreshTokenRequest(); request.setRefreshToken("raw-token");

        assertThrows(UnauthorizedException.class, () -> service.refresh(request));
        assertNotNull(token.getRevokedAt());
        verify(refreshTokens).save(token);
    }

    @Test
    void logoutRevokesRefreshTokensAndInvalidatesAccessTokens() {
        User user = user();
        LocalDateTime before = LocalDateTime.now();
        user.setPasswordChangedAt(before);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        service.logout(7L);
        verify(refreshTokens).revokeAllByUserId(7L);
        verify(users).save(user);
        assertNotNull(user.getPasswordChangedAt());
        assertTrue(user.getPasswordChangedAt().isAfter(before));
    }

    @Test
    void expiredAccessSessionCanStillLogoutThroughRefreshToken() {
        User user = user();
        RefreshToken token = token(user);
        when(refreshTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        service.logoutByRefreshToken("raw-refresh-token");

        verify(refreshTokens).revokeAllByUserId(7L);
        verify(users).save(user);
    }

    private User user() {
        User user = new User(); user.setUserId(7L); user.setEmail("user@example.com"); user.setName("User");
        user.setIsActive(true); user.setAccountLocked(false); user.setPasswordChangedAt(LocalDateTime.now().minusDays(1));
        return user;
    }

    private RefreshToken token(User user) {
        RefreshToken token = new RefreshToken(); token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(1)); return token;
    }
}
