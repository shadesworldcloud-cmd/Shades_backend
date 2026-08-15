package com.sunglassstore.service;

import com.sunglassstore.dto.request.LoginRequest;
import com.sunglassstore.dto.request.RefreshTokenRequest;
import com.sunglassstore.dto.request.RegisterRequest;
import com.sunglassstore.dto.request.GoogleAuthRequest;
import com.sunglassstore.dto.response.AuthResponse;
import com.sunglassstore.dto.response.UserResponse;

public interface AuthenticationService {
    void register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse loginWithGoogle(GoogleAuthRequest request);
    AuthResponse refresh(RefreshTokenRequest request);
    void logout(Long userId);
    void logoutByRefreshToken(String refreshToken);
    UserResponse getCurrentUser(Long userId);
}
