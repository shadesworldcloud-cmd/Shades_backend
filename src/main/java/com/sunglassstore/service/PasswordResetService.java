package com.sunglassstore.service;

import com.sunglassstore.dto.request.ForgotPasswordRequest;
import com.sunglassstore.dto.request.ResetPasswordRequest;

public interface PasswordResetService {
    void requestReset(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
