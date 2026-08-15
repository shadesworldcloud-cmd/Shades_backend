package com.sunglassstore.service;

import com.sunglassstore.dto.request.UpdateCommunicationPreferencesRequest;
import com.sunglassstore.entity.CustomerCommunicationPreference;
import com.sunglassstore.entity.User;
import com.sunglassstore.repository.CustomerCommunicationPreferenceRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.impl.CommunicationPreferenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunicationPreferenceServiceImplTest {
    private CustomerCommunicationPreferenceRepository preferences;
    private UserRepository users;
    private CommunicationPreferenceServiceImpl service;

    @BeforeEach void setUp() {
        preferences = mock(CustomerCommunicationPreferenceRepository.class);
        users = mock(UserRepository.class);
        service = new CommunicationPreferenceServiceImpl(preferences, users);
        when(preferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void missingPreferenceUsesCompatibleEnabledDefaultWithoutWritingDuringDelivery() {
        assertTrue(service.allowsEmail("customer@example.com", CommunicationPreferenceService.Topic.SHIPMENT));
        assertTrue(service.allowsInApp(7L, CommunicationPreferenceService.Topic.ORDER));
        verify(preferences, never()).save(any());
    }

    @Test void getCreatesOneToOneDefaultsForTheAuthenticatedUser() {
        User user = new User(); user.setUserId(7L); when(users.findById(7L)).thenReturn(Optional.of(user));
        var result = service.get(7L);
        assertTrue(result.emailOrderUpdates()); assertTrue(result.inAppReviewUpdates());
        verify(preferences).save(argThat(value -> value.getUser() == user));
    }

    @Test void updatePersistsEveryExplicitChoice() {
        CustomerCommunicationPreference existing = new CustomerCommunicationPreference();
        when(preferences.findById(7L)).thenReturn(Optional.of(existing));
        UpdateCommunicationPreferencesRequest request = new UpdateCommunicationPreferencesRequest();
        request.setEmailOrderUpdates(false); request.setEmailShipmentUpdates(true); request.setEmailReturnRefundUpdates(false);
        request.setInAppOrderUpdates(true); request.setInAppShipmentUpdates(false); request.setInAppReturnRefundUpdates(true); request.setInAppReviewUpdates(false);
        var result = service.update(7L, request);
        assertFalse(result.emailOrderUpdates()); assertFalse(result.emailReturnRefundUpdates());
        assertFalse(result.inAppShipmentUpdates()); assertFalse(result.inAppReviewUpdates());
    }
}
