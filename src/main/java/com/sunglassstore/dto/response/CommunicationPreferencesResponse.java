package com.sunglassstore.dto.response;

import com.sunglassstore.entity.CustomerCommunicationPreference;
import java.time.LocalDateTime;

public record CommunicationPreferencesResponse(
        boolean emailOrderUpdates, boolean emailShipmentUpdates, boolean emailReturnRefundUpdates,
        boolean inAppOrderUpdates, boolean inAppShipmentUpdates, boolean inAppReturnRefundUpdates,
        boolean inAppReviewUpdates, LocalDateTime updatedAt) {
    public static CommunicationPreferencesResponse fromEntity(CustomerCommunicationPreference value) {
        return new CommunicationPreferencesResponse(Boolean.TRUE.equals(value.getEmailOrderUpdates()),
                Boolean.TRUE.equals(value.getEmailShipmentUpdates()), Boolean.TRUE.equals(value.getEmailReturnRefundUpdates()),
                Boolean.TRUE.equals(value.getInAppOrderUpdates()), Boolean.TRUE.equals(value.getInAppShipmentUpdates()),
                Boolean.TRUE.equals(value.getInAppReturnRefundUpdates()), Boolean.TRUE.equals(value.getInAppReviewUpdates()),
                value.getUpdatedAt());
    }
}
