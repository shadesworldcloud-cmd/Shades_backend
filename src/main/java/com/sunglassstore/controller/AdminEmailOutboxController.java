package com.sunglassstore.controller;

import com.sunglassstore.dto.response.EmailOutboxAdminResponse;
import com.sunglassstore.dto.response.EmailOutboxSummaryResponse;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import com.sunglassstore.service.AdminEmailOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/email-outbox")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
public class AdminEmailOutboxController {
    private final AdminEmailOutboxService service;

    @GetMapping
    public ResponseEntity<Page<EmailOutboxAdminResponse>> getMessages(
            @RequestParam(required = false) EmailOutboxStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(service.getMessages(status, search, pageable));
    }

    @GetMapping("/summary")
    public ResponseEntity<EmailOutboxSummaryResponse> getSummary() {
        return ResponseEntity.ok(service.getSummary());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<EmailOutboxAdminResponse> retry(@PathVariable Long id) {
        return ResponseEntity.ok(service.retry(id));
    }
}
