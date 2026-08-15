package com.sunglassstore.email.event;

public record PasswordResetEmailRequested(String email, String customerName, String rawToken) {}
