package com.nexusbank.userservice.controller;

import com.nexusbank.userservice.dto.response.KycStatusResponse;
import com.nexusbank.userservice.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal API for inter-service calls only — not routed through the public
 * API gateway. Account Service calls this to enforce KYC before opening an
 * account (real service-level isolation, not just a UI guard).
 */
@RestController
@RequestMapping("/api/internal/customers")
class CustomerInternalController {

    private final CustomerService customerService;

    CustomerInternalController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/{customerId}/kyc")
    public ResponseEntity<KycStatusResponse> getKycStatus(@PathVariable Long customerId) {
        return ResponseEntity.ok(
                new KycStatusResponse(customerId, customerService.getKycStatus(customerId)));
    }

    /**
     * Resolves a customer profile ID → the owning User's ID.
     * Used by transaction-service to verify transfer ownership against the JWT subject.
     */
    @GetMapping("/{customerId}/user-id")
    public ResponseEntity<Map<String, Long>> getUserId(@PathVariable Long customerId) {
        Long userId = customerService.getCustomer(customerId).getUserId();
        return ResponseEntity.ok(Map.of("userId", userId));
    }
}
