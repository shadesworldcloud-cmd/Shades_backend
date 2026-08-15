package com.sunglassstore.service;

import com.sunglassstore.dto.request.ForgotPasswordRequest;
import com.sunglassstore.dto.request.ResetPasswordRequest;
import com.sunglassstore.entity.PasswordResetToken;
import com.sunglassstore.entity.User;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.PasswordResetTokenRepository;
import com.sunglassstore.repository.RefreshTokenRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.impl.PasswordResetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.sunglassstore.email.event.PasswordResetEmailRequested;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetServiceImplTest {
    private UserRepository users;
    private PasswordResetTokenRepository tokens;
    private RefreshTokenRepository refreshTokens;
    private PasswordEncoder encoder;
    private ApplicationEventPublisher events;
    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class); tokens = mock(PasswordResetTokenRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class); encoder = mock(PasswordEncoder.class);
        events = mock(ApplicationEventPublisher.class);
        service = new PasswordResetServiceImpl(users, tokens, refreshTokens, encoder, events);
    }

    @Test
    void unknownEmailReturnsSilentlyWithoutCreatingToken() {
        ForgotPasswordRequest request = forgot("missing@example.com");
        when(users.findByEmailIgnoreCaseForUpdate("missing@example.com")).thenReturn(Optional.empty());

        service.requestReset(request);

        verify(tokens, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void activeUserReceivesOneTimeResetLink() {
        User user = user();
        when(users.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(tokens.findTopByUserUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());

        service.requestReset(forgot(user.getEmail()));

        verify(tokens).invalidateAllForUser(7L);
        verify(tokens).save(argThat(token -> token.getTokenHash() != null && token.getExpiresAt().isAfter(LocalDateTime.now())));
        verify(events).publishEvent(any(PasswordResetEmailRequested.class));
    }

    @Test
    void validTokenChangesPasswordUnlocksAndRevokesSessions() {
        User user = user(); user.setAccountLocked(true); user.setFailedLoginAttempts(4);
        PasswordResetToken stored = new PasswordResetToken(); stored.setUser(user);
        stored.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(tokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(stored));
        when(encoder.encode("new-password")).thenReturn("new-hash");
        ResetPasswordRequest request = new ResetPasswordRequest(); request.setToken("raw-token"); request.setNewPassword("new-password");

        service.resetPassword(request);

        verify(users).save(user);
        verify(tokens).invalidateAllForUser(7L);
        verify(refreshTokens).revokeAllByUserId(7L);
        assertFalse(user.getAccountLocked());
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void expiredTokenIsRejected() {
        PasswordResetToken stored = new PasswordResetToken(); stored.setUser(user());
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(tokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(stored));
        ResetPasswordRequest request = new ResetPasswordRequest(); request.setToken("raw-token"); request.setNewPassword("new-password");

        assertThrows(BadRequestException.class, () -> service.resetPassword(request));
        verify(users, never()).save(any());
    }

    @Test
    void usedTokenIsRejectedWithoutChangingPasswordOrSessions() {
        PasswordResetToken stored = new PasswordResetToken(); stored.setUser(user());
        stored.setExpiresAt(LocalDateTime.now().plusMinutes(5)); stored.setUsedAt(LocalDateTime.now().minusSeconds(1));
        when(tokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(stored));
        ResetPasswordRequest request = new ResetPasswordRequest(); request.setToken("raw-token"); request.setNewPassword("new-password");

        assertThrows(BadRequestException.class, () -> service.resetPassword(request));

        verifyNoInteractions(encoder, refreshTokens);
        verify(users, never()).save(any());
    }

    @Test
    void recentRequestIsThrottledWithoutReplacingTokenOrSendingEmail() {
        User user = user(); PasswordResetToken recent = new PasswordResetToken();
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        when(users.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(tokens.findTopByUserUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(recent));

        service.requestReset(forgot("  CUSTOMER@example.com  "));

        verify(tokens, never()).invalidateAllForUser(any());
        verify(tokens, never()).save(any());
        verifyNoInteractions(events);
    }

    private ForgotPasswordRequest forgot(String email) { ForgotPasswordRequest request = new ForgotPasswordRequest(); request.setEmail(email); return request; }
    private User user() { User user = new User(); user.setUserId(7L); user.setEmail("customer@example.com"); user.setIsActive(true); return user; }
}
