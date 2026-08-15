package com.sunglassstore.service;

import com.sunglassstore.email.event.EmailVerificationRequested;
import com.sunglassstore.entity.EmailVerificationToken;
import com.sunglassstore.entity.User;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.EmailVerificationTokenRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.impl.EmailVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailVerificationServiceImplTest {
    private UserRepository users;
    private EmailVerificationTokenRepository tokens;
    private ApplicationEventPublisher events;
    private EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(EmailVerificationTokenRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new EmailVerificationServiceImpl(users, tokens, events);
    }

    @Test
    void requestStoresOnlyHashAndPublishesRawToken() {
        User user = user();
        when(users.findByEmailIgnoreCaseForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(tokens.findTopByUserUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());

        service.requestVerification(" User@Example.com ");

        verify(tokens).invalidateAllForUser(7L);
        var tokenCaptor = org.mockito.ArgumentCaptor.forClass(EmailVerificationToken.class);
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(EmailVerificationRequested.class);
        verify(tokens).save(tokenCaptor.capture());
        verify(events).publishEvent(eventCaptor.capture());
        assertNotEquals(eventCaptor.getValue().rawToken(), tokenCaptor.getValue().getTokenHash());
        assertTrue(tokenCaptor.getValue().getExpiresAt().isAfter(LocalDateTime.now().plusHours(23)));
    }

    @Test
    void resendWithinOneMinuteDoesNotCreateAnotherToken() {
        User user = user(); EmailVerificationToken recent = new EmailVerificationToken();
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(20));
        when(users.findByEmailIgnoreCaseForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(tokens.findTopByUserUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(recent));
        service.requestVerification("user@example.com");
        verify(tokens, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void unknownAndAlreadyVerifiedAccountsDoNotLeakOrSend() {
        service.requestVerification("missing@example.com");
        User verified = user(); verified.setEmailVerified(true);
        when(users.findByEmailIgnoreCaseForUpdate("verified@example.com")).thenReturn(Optional.of(verified));
        service.requestVerification("verified@example.com");
        verifyNoInteractions(tokens, events);
    }

    @Test
    void validTokenVerifiesUserAndInvalidatesAllLinks() {
        User user = user(); EmailVerificationToken token = token(user, LocalDateTime.now().plusMinutes(5));
        when(tokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token));
        service.verify("raw-token");
        assertTrue(user.getEmailVerified());
        verify(users).save(user);
        verify(tokens).invalidateAllForUser(7L);
    }

    @Test
    void expiredUsedAndUnknownTokensAreRejected() {
        when(tokens.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.verify("unknown"));

        EmailVerificationToken expired = token(user(), LocalDateTime.now().minusSeconds(1));
        when(tokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(expired));
        assertThrows(BadRequestException.class, () -> service.verify("expired"));

        EmailVerificationToken used = token(user(), LocalDateTime.now().plusMinutes(5));
        used.setVerifiedAt(LocalDateTime.now());
        when(tokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(used));
        assertThrows(BadRequestException.class, () -> service.verify("used"));
    }

    private User user() {
        User user = new User(); user.setUserId(7L); user.setEmail("user@example.com");
        user.setName("User"); user.setIsActive(true); user.setEmailVerified(false); return user;
    }

    private EmailVerificationToken token(User user, LocalDateTime expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken(); token.setUser(user);
        token.setExpiresAt(expiresAt); return token;
    }
}
