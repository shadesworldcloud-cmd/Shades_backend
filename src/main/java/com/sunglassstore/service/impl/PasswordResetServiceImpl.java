package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.ForgotPasswordRequest;
import com.sunglassstore.dto.request.ResetPasswordRequest;
import com.sunglassstore.entity.PasswordResetToken;
import com.sunglassstore.entity.User;
import com.sunglassstore.email.event.PasswordResetEmailRequested;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.PasswordResetTokenRepository;
import com.sunglassstore.repository.RefreshTokenRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class PasswordResetServiceImpl implements PasswordResetService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCaseForUpdate(email).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) return;

        var latest = tokenRepository.findTopByUserUserIdOrderByCreatedAtDesc(user.getUserId());
        if (latest.isPresent() && latest.get().getCreatedAt() != null
                && latest.get().getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1))) return;

        tokenRepository.invalidateAllForUser(user.getUserId());
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        tokenRepository.save(token);

        eventPublisher.publishEvent(new PasswordResetEmailRequested(user.getEmail(), user.getName(), rawToken));
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(hash(request.getToken().trim()))
                .orElseThrow(() -> new BadRequestException("This reset link is invalid or expired"));
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("This reset link is invalid or expired");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(nextCredentialVersion(user.getPasswordChangedAt()));
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockedUntil(null);
        userRepository.save(user);
        tokenRepository.invalidateAllForUser(user.getUserId());
        refreshTokenRepository.revokeAllByUserId(user.getUserId());
    }

    private LocalDateTime nextCredentialVersion(LocalDateTime currentVersion) {
        LocalDateTime now = LocalDateTime.now();
        if (currentVersion != null && !now.isAfter(currentVersion.plusSeconds(1))) {
            return currentVersion.plusSeconds(1);
        }
        return now;
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
