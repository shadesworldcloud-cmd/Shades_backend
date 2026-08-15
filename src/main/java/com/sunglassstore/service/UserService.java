package com.sunglassstore.service;

import com.sunglassstore.dto.request.UpdateProfileRequest;
import com.sunglassstore.entity.User;

public interface UserService {

    User findById(Long userId);

    User findByEmail(String email);

    User updateProfile(Long userId, UpdateProfileRequest request);
}
