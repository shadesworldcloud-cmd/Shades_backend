package com.sunglassstore.service;

public interface EmailVerificationService {
    void requestVerification(String email);
    void verify(String rawToken);
}
