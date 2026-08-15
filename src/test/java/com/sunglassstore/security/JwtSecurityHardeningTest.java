package com.sunglassstore.security;

import com.sunglassstore.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtSecurityHardeningTest {
    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhtYWMtc2hhMjU2LWFsZ29yaXRobQ==";

    @Test
    void tokenRequiresCurrentPasswordVersionAndActiveUnlockedAccount() {
        JwtService jwt = new JwtService(SECRET, 900_000, 604_800_000);
        User user = user();
        SecurityUser initial = new SecurityUser(user);
        String token = jwt.generateAccessToken(initial);

        assertTrue(jwt.isTokenValid(token, initial));

        user.setIsActive(false);
        assertFalse(jwt.isTokenValid(token, new SecurityUser(user)));
        user.setIsActive(true);
        user.setPasswordChangedAt(user.getPasswordChangedAt().plusHours(11));
        assertTrue(jwt.isTokenValid(token, new SecurityUser(user)));
        user.setPasswordHash("replacement-hash");
        assertFalse(jwt.isTokenValid(token, new SecurityUser(user)));
        user.setPasswordHash("hash");
        user.setAccountLocked(true);
        assertFalse(jwt.isTokenValid(token, new SecurityUser(user)));
    }

    private User user() {
        User user = new User(); user.setUserId(7L); user.setEmail("customer@example.com");
        user.setName("Customer"); user.setPasswordHash("hash"); user.setIsActive(true);
        user.setAccountLocked(false); user.setPasswordChangedAt(LocalDateTime.now().withNano(0));
        return user;
    }
}
