package com.sunglassstore.service.impl;

import com.sunglassstore.email.event.EmailVerificationRequested;
import com.sunglassstore.entity.EmailVerificationToken;
import com.sunglassstore.entity.User;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.EmailVerificationTokenRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void requestVerification(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCaseForUpdate(normalized).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())
                || Boolean.TRUE.equals(user.getEmailVerified())) return;

        var latest = tokenRepository.findTopByUserUserIdOrderByCreatedAtDesc(user.getUserId());
        if (latest.isPresent() && latest.get().getCreatedAt() != null
                && latest.get().getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1))) return;

        tokenRepository.invalidateAllForUser(user.getUserId());
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenRepository.save(token);
        eventPublisher.publishEvent(new EmailVerificationRequested(user.getEmail(), user.getName(), rawToken));
    }

    @Override
    @Transactional
    public void verify(String rawToken) {
        String normalized = rawToken == null ? "" : rawToken.trim();
        EmailVerificationToken token = tokenRepository.findByTokenHashForUpdate(hash(normalized))
                .orElseThrow(() -> new BadRequestException("This verification link is invalid or expired"));
        if (token.getVerifiedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("This verification link is invalid or expired");
        }
        User user = token.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("This account is unavailable");
        }
        user.setEmailVerified(true);
        userRepository.save(user);
        tokenRepository.invalidateAllForUser(user.getUserId());
    }

    private String hash(String rawToken) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
