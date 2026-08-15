package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Notification;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

public record NotificationResponse(Long notificationId, String type, String subject, String message,
                                   boolean read, LocalDateTime readAt, LocalDateTime createdAt,
                                   String actionUrl) {
    private static final Pattern REVIEW_PRODUCT = Pattern.compile("review\\s+(?:approved|rejected)\\s+·\\s+product\\s+#(\\d+)", Pattern.CASE_INSENSITIVE);

    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(notification.getNotificationId(), notification.getNotificationType().name(),
                notification.getSubject(), notification.getMessage(), notification.getReadAt() != null,
                notification.getReadAt(), notification.getCreatedAt(), actionUrl(notification));
    }

    private static String actionUrl(Notification notification) {
        String value = (notification.getSubject() + " " + notification.getMessage()).toLowerCase();
        if (value.contains("return") || value.contains("refund") || value.contains("order")) return "/my-orders";
        var reviewProduct = REVIEW_PRODUCT.matcher(notification.getSubject());
        if (reviewProduct.find()) return "/product/" + reviewProduct.group(1);
        return null;
    }
}
