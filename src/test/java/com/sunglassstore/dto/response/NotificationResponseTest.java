package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Notification;
import com.sunglassstore.entity.enums.NotificationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationResponseTest {
    @Test
    void reviewDecisionLinksToReviewedProduct() {
        Notification notification = new Notification();
        notification.setNotificationType(NotificationType.IN_APP);
        notification.setSubject("Review approved · Product #13");
        notification.setMessage("Your review is now visible to shoppers.");

        assertEquals("/product/13", NotificationResponse.fromEntity(notification).actionUrl());
    }
}
