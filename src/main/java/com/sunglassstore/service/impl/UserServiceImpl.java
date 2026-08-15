package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.UpdateProfileRequest;
import com.sunglassstore.entity.User;
import com.sunglassstore.exception.OptimisticLockConflictException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        String name = request.getName().trim();
        String phone = request.getPhoneNumber();
        // Canonical E.164 for storage. @IndianMobile has already rejected anything unacceptable,
        // so this only reshapes a value that is known good.
        String normalizedPhone = com.sunglassstore.validation.PhoneNumbers.toStored(phone);
        Long expectedVersion = request.getVersion();
        int updated = expectedVersion == null
                // Legacy path: no version supplied, so no concurrency check is possible. Kept so an
                // older client is not broken outright; every client in this repository sends one.
                ? userRepository.updateEditableProfile(userId, name, normalizedPhone)
                : userRepository.updateEditableProfileIfVersionMatches(userId, name, normalizedPhone, expectedVersion);
        if (updated == 0) {
            // Zero rows is ambiguous on its own: the row may be missing, or the version may have
            // moved. Distinguish them so the caller gets 404 or 409 rather than a guess.
            boolean stillExists = userRepository.findByIdWithRoles(userId).isPresent();
            if (stillExists && expectedVersion != null) {
                throw new OptimisticLockConflictException(
                        "This information was updated elsewhere. Refresh and review the latest version before trying again.");
            }
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
