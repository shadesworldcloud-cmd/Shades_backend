package com.sunglassstore.email.event;

public record EmailVerificationRequested(String email, String customerName, String rawToken) {}
