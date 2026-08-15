package com.sunglassstore.controller;

import com.sunglassstore.dto.response.AdminCustomerResponse;
import com.sunglassstore.service.AdminCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
public class AdminCustomerController {
    private final AdminCustomerService customerService;

    @GetMapping
    public ResponseEntity<Page<AdminCustomerResponse>> getCustomers(Pageable pageable) {
        return ResponseEntity.ok(customerService.getCustomers(pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminCustomerResponse> getCustomer(@PathVariable Long userId) {
        return ResponseEntity.ok(customerService.getCustomer(userId));
    }

    @PatchMapping("/{userId}/active")
    public ResponseEntity<AdminCustomerResponse> setActive(@PathVariable Long userId,
                                                            @RequestParam boolean active) {
        return ResponseEntity.ok(customerService.setActive(userId, active));
    }
}
