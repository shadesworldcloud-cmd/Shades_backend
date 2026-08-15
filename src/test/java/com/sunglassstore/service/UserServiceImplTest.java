package com.sunglassstore.service;

import com.sunglassstore.dto.request.UpdateProfileRequest;
import com.sunglassstore.entity.User;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest {
    @Test
    void updatesOnlyEditableProfileFields() {
        UserRepository repository = mock(UserRepository.class);
        User user = new User();
        user.setUserId(4L);
        user.setEmail("fixed@example.com");
        user.setName("Old Name");
        LocalDateTime credentialVersion = LocalDateTime.of(2026, 8, 2, 11, 34, 52);
        user.setPasswordChangedAt(credentialVersion);
        when(repository.updateEditableProfile(4L, "New Name", "+919876543210")).thenAnswer(invocation -> {
            user.setName(invocation.getArgument(1));
            user.setPhoneNumber(invocation.getArgument(2));
            return 1;
        });
        when(repository.findByIdWithRoles(4L)).thenReturn(Optional.of(user));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("  New Name  ");
        request.setPhoneNumber("  +91 98765 43210  ");

        User updated = new UserServiceImpl(repository).updateProfile(4L, request);

        assertEquals("New Name", updated.getName());
        // Normalised to canonical E.164 rather than merely trimmed: PhoneNumbers.toStored turns
        // "  +91 98765 43210  " into "+919876543210" so one number has one representation.
        assertEquals("+919876543210", updated.getPhoneNumber());
        assertEquals("fixed@example.com", updated.getEmail());
        assertEquals(credentialVersion, updated.getPasswordChangedAt());
        verify(repository).updateEditableProfile(4L, "New Name", "+919876543210");
        verify(repository, never()).save(any());
    }
}
