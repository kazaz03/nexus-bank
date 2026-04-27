package com.nexusbank.loanservice.controller;

import com.github.fge.jsonpatch.JsonPatch;
import com.nexusbank.loanservice.dto.request.LoanApplicationRequest;
import com.nexusbank.loanservice.dto.request.LoanReviewRequest;
import com.nexusbank.loanservice.dto.response.LoanApplicationResponse;
import com.nexusbank.loanservice.dto.response.RepaymentScheduleResponse;
import com.nexusbank.loanservice.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    public ResponseEntity<LoanApplicationResponse> submitApplication(
            @Valid @RequestBody LoanApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.submitApplication(request));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<LoanApplicationResponse>> submitApplicationsBatch(
            @Valid @RequestBody List<@Valid LoanApplicationRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.submitApplicationsBatch(requests));
    }

    @GetMapping
    public ResponseEntity<Page<LoanApplicationResponse>> getAllApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        return ResponseEntity.ok(loanService.getAllApplications(
                page,
                size,
                sortBy,
                sortDirection,
                customerId,
                status,
                minAmount,
                maxAmount));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanApplicationResponse> getApplication(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.getApplication(id));
    }

    @PatchMapping(path = "/{id}", consumes = "application/json-patch+json")
    public ResponseEntity<LoanApplicationResponse> patchApplication(
            @PathVariable Long id,
            @RequestBody JsonPatch patch) {
        return ResponseEntity.ok(loanService.patchApplication(id, patch));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<LoanApplicationResponse>> getApplicationsByCustomer(
            @PathVariable Long customerId) {
        return ResponseEntity.ok(loanService.getApplicationsByCustomer(customerId));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<LoanApplicationResponse> reviewApplication(
            @PathVariable Long id,
            @Valid @RequestBody LoanReviewRequest request) {
        return ResponseEntity.ok(loanService.reviewApplication(id, request));
    }

    @GetMapping("/{id}/schedule")
    public ResponseEntity<List<RepaymentScheduleResponse>> getRepaymentSchedule(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.getRepaymentSchedule(id));
    }

    @GetMapping("/probe/account-instance")
    public ResponseEntity<Map<String, Object>> probeAccountServiceInstance(
            @RequestParam(defaultValue = "lb") String mode,
            @RequestParam(required = false) String directBaseUrl) {
        return ResponseEntity.ok(loanService.probeAccountServiceInstance(mode, directBaseUrl));
    }
}
