package com.sunglassstore.controller;

import com.sunglassstore.dto.request.LoginRequest;
import com.sunglassstore.dto.request.GoogleAuthRequest;
import com.sunglassstore.dto.request.RefreshTokenRequest;
import com.sunglassstore.dto.request.RegisterRequest;
import com.sunglassstore.dto.request.ForgotPasswordRequest;
import com.sunglassstore.dto.request.ResetPasswordRequest;
import com.sunglassstore.dto.request.UpdateProfileRequest;
import com.sunglassstore.dto.request.VerifyEmailRequest;
import com.sunglassstore.dto.response.AuthResponse;
import com.sunglassstore.dto.response.MessageResponse;
import com.sunglassstore.dto.response.UserResponse;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.security.AuthCookieService;
import com.sunglassstore.exception.UnauthorizedException;
import com.sunglassstore.service.AuthenticationService;
import com.sunglassstore.service.PasswordResetService;
import com.sunglassstore.service.UserService;
import com.sunglassstore.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final UserService userService;
    private final AuthCookieService authCookieService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Account created. Check your email to verify your account."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.getToken());
        return ResponseEntity.ok(new MessageResponse("Email verified successfully. You can now sign in."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        emailVerificationService.requestVerification(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "If an eligible unverified account exists, a link has been sent or a recently requested link remains valid."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthResponse auth = authenticationService.login(request);
        authCookieService.write(response, auth);
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleAuthRequest request,
                                                        HttpServletResponse response) {
        AuthResponse auth = authenticationService.loginWithGoogle(request);
        authCookieService.write(response, auth);
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Session expired. Please sign in again.");
        }
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);
        AuthResponse auth = authenticationService.refresh(request);
        authCookieService.write(response, auth);
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.ok(new MessageResponse("If an active account exists, a reset link will be sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Password reset successfully. You can now sign in."));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
                                                  @AuthenticationPrincipal SecurityUser principal,
                                                  @CookieValue(name = AuthCookieService.REFRESH_COOKIE,
                                                          required = false) String refreshToken,
                                                  HttpServletResponse response) {
        if (principal != null) authenticationService.logout(principal.getUserId());
        else authenticationService.logoutByRefreshToken(refreshToken);
        authCookieService.clear(response);
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(authenticationService.getCurrentUser(principal.getUserId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(@AuthenticationPrincipal SecurityUser principal,
                                                           @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(UserResponse.fromEntity(userService.updateProfile(principal.getUserId(), request)));
    }
}
