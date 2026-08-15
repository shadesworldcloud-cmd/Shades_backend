package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.UpdateCommunicationPreferencesRequest;
import com.sunglassstore.dto.response.CommunicationPreferencesResponse;
import com.sunglassstore.entity.CustomerCommunicationPreference;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.CustomerCommunicationPreferenceRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.CommunicationPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class CommunicationPreferenceServiceImpl implements CommunicationPreferenceService {
    private final CustomerCommunicationPreferenceRepository preferences;
    private final UserRepository users;

    @Override @Transactional
    public CommunicationPreferencesResponse get(Long userId) { return CommunicationPreferencesResponse.fromEntity(findOrCreate(userId)); }

    @Override @Transactional
    public CommunicationPreferencesResponse update(Long userId, UpdateCommunicationPreferencesRequest request) {
        var value = findOrCreate(userId);
        value.setEmailOrderUpdates(request.getEmailOrderUpdates());
        value.setEmailShipmentUpdates(request.getEmailShipmentUpdates());
        value.setEmailReturnRefundUpdates(request.getEmailReturnRefundUpdates());
        value.setInAppOrderUpdates(request.getInAppOrderUpdates());
        value.setInAppShipmentUpdates(request.getInAppShipmentUpdates());
        value.setInAppReturnRefundUpdates(request.getInAppReturnRefundUpdates());
        value.setInAppReviewUpdates(request.getInAppReviewUpdates());
        return CommunicationPreferencesResponse.fromEntity(preferences.save(value));
    }

    @Override @Transactional(readOnly = true)
    public boolean allowsEmail(String email, Topic topic) {
        return preferences.findByUserEmailIgnoreCase(email).map(value -> emailValue(value, topic)).orElse(true);
    }

    @Override @Transactional(readOnly = true)
    public boolean allowsInApp(Long userId, Topic topic) {
        return preferences.findById(userId).map(value -> inAppValue(value, topic)).orElse(true);
    }

    private CustomerCommunicationPreference findOrCreate(Long userId) {
        return preferences.findById(userId).orElseGet(() -> {
            var value = new CustomerCommunicationPreference();
            value.setUser(users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found")));
            return preferences.save(value);
        });
    }
    private boolean emailValue(CustomerCommunicationPreference p, Topic topic) {
        return switch (topic) {
            case ORDER -> Boolean.TRUE.equals(p.getEmailOrderUpdates());
            case SHIPMENT -> Boolean.TRUE.equals(p.getEmailShipmentUpdates());
            case RETURN_REFUND -> Boolean.TRUE.equals(p.getEmailReturnRefundUpdates());
            case REVIEW -> true;
        };
    }
    private boolean inAppValue(CustomerCommunicationPreference p, Topic topic) {
        return switch (topic) {
            case ORDER -> Boolean.TRUE.equals(p.getInAppOrderUpdates());
            case SHIPMENT -> Boolean.TRUE.equals(p.getInAppShipmentUpdates());
            case RETURN_REFUND -> Boolean.TRUE.equals(p.getInAppReturnRefundUpdates());
            case REVIEW -> Boolean.TRUE.equals(p.getInAppReviewUpdates());
        };
    }
}
