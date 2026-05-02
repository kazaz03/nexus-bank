package com.nexusbank.accountservice.controller;

import com.nexusbank.accountservice.dto.request.BalanceUpdateRequest;
import com.nexusbank.accountservice.dto.response.AccountInternalResponse;
import com.nexusbank.accountservice.dto.response.BalanceUpdateResponse;
import com.nexusbank.accountservice.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API surface used exclusively by other microservices via
 * synchronous HTTP calls
 *
 * Endpoints are grouped under "/api/accounts/internal" to separate
 * them from the public client-facing API in {@link AccountController}.
 */
@RestController
@RequestMapping("/api/accounts/internal")
public class AccountInternalController {

    private final AccountService accountService;

    public AccountInternalController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/by-iban/{iban}")
    public ResponseEntity<AccountInternalResponse> getByIban(@PathVariable String iban) {
        return ResponseEntity.ok(accountService.getInternalByIban(iban));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountInternalResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getInternalById(id));
    }

    @PostMapping("/{id}/debit")
    public ResponseEntity<BalanceUpdateResponse> debit(
            @PathVariable Long id,
            @Valid @RequestBody BalanceUpdateRequest request) {
        return ResponseEntity.ok(accountService.debit(id, request));
    }

    @PostMapping("/{id}/credit")
    public ResponseEntity<BalanceUpdateResponse> credit(
            @PathVariable Long id,
            @Valid @RequestBody BalanceUpdateRequest request) {
        return ResponseEntity.ok(accountService.credit(id, request));
    }
}
