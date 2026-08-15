package com.sunglassstore.controller;

import com.sunglassstore.dto.request.UpdateCommunicationPreferencesRequest;
import com.sunglassstore.dto.response.CommunicationPreferencesResponse;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.CommunicationPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/communication-preferences") @RequiredArgsConstructor
public class CommunicationPreferenceController {
    private final CommunicationPreferenceService service;
    @GetMapping
    public ResponseEntity<CommunicationPreferencesResponse> get(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(service.get(principal.getUserId()));
    }
    @PutMapping
    public ResponseEntity<CommunicationPreferencesResponse> update(@AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody UpdateCommunicationPreferencesRequest request) {
        return ResponseEntity.ok(service.update(principal.getUserId(), request));
    }
}
