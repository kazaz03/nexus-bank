package com.nexusbank.userservice.controller;

import com.nexusbank.userservice.dto.request.RegisterCustomerRequest;
import com.nexusbank.userservice.dto.request.UpdateCustomerRequest;
import com.nexusbank.userservice.dto.response.CustomerResponse;
import com.nexusbank.userservice.security.JwtUtil;
import com.nexusbank.userservice.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final JwtUtil jwtUtil;

    public CustomerController(CustomerService customerService, JwtUtil jwtUtil) {
        this.customerService = customerService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<CustomerResponse> registerCustomer(
            @Valid @RequestBody RegisterCustomerRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long actorId = extractUserId(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerService.registerCustomer(request, actorId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomer(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @RequestBody UpdateCustomerRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long actorId = extractUserId(authHeader);
        return ResponseEntity.ok(customerService.updateCustomer(id, request, actorId));
    }

    private Long extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return jwtUtil.extractUserId(authHeader.substring(7));
        }
        return null;
    }
}
