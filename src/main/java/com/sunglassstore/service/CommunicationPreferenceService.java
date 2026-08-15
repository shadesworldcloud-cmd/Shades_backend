package com.sunglassstore.service;

import com.sunglassstore.dto.request.UpdateCommunicationPreferencesRequest;
import com.sunglassstore.dto.response.CommunicationPreferencesResponse;

public interface CommunicationPreferenceService {
    enum Topic { ORDER, SHIPMENT, RETURN_REFUND, REVIEW }
    CommunicationPreferencesResponse get(Long userId);
    CommunicationPreferencesResponse update(Long userId, UpdateCommunicationPreferencesRequest request);
    boolean allowsEmail(String email, Topic topic);
    boolean allowsInApp(Long userId, Topic topic);
}
