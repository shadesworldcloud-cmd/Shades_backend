package com.sunglassstore.controller;

import com.sunglassstore.dto.response.NotificationResponse;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal SecurityUser principal, Pageable pageable) {
        return ResponseEntity.ok(notificationService.getUserNotifications(principal.getUserId(), pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<java.util.Map<String, Long>> getUnreadCount(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(java.util.Map.of("unreadCount", notificationService.getUnreadCount(principal.getUserId())));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(@AuthenticationPrincipal SecurityUser principal,
                                                          @PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationService.markRead(principal.getUserId(), notificationId));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<java.util.Map<String, Long>> markAllRead(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(java.util.Map.of("updated", notificationService.markAllRead(principal.getUserId())));
    }
}
