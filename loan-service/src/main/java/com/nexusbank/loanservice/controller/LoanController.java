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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<LoanApplicationResponse> submitApplication(
            @Valid @RequestBody LoanApplicationRequest request,
            Authentication auth) {
        Long callerUserId = parseUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.submitApplication(request, callerUserId));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<List<LoanApplicationResponse>> submitApplicationsBatch(
            @Valid @RequestBody List<@Valid LoanApplicationRequest> requests,
            Authentication auth) {
        Long callerUserId = parseUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.submitApplicationsBatch(requests, callerUserId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LOAN_OFFICER', 'TELLER', 'ADMIN')")
    public ResponseEntity<LoanApplicationResponse> getApplication(
            @PathVariable Long id,
            Authentication auth) {
        Long callerUserId = parseUserId(auth);
        boolean isCustomer = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
        return ResponseEntity.ok(loanService.getApplication(id, callerUserId, isCustomer));
    }

    @PatchMapping(path = "/{id}", consumes = "application/json-patch+json")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<LoanApplicationResponse> patchApplication(
            @PathVariable Long id,
            @RequestBody JsonPatch patch) {
        return ResponseEntity.ok(loanService.patchApplication(id, patch));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LOAN_OFFICER', 'TELLER', 'ADMIN')")
    public ResponseEntity<List<LoanApplicationResponse>> getApplicationsByCustomer(
            @PathVariable Long customerId) {
        return ResponseEntity.ok(loanService.getApplicationsByCustomer(customerId));
    }

    /**
     * Reviews (approves or rejects) a loan application.
     * For approvals the disbursement saga is started asynchronously via RabbitMQ;
     * the response is 202 Accepted to signal that the action has begun but has not
     * yet completed. A push notification will arrive via SSE when the saga finishes.
     * Rejections are synchronous and return 200 OK.
     */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    public ResponseEntity<LoanApplicationResponse> reviewApplication(
            @PathVariable Long id,
            @Valid @RequestBody LoanReviewRequest request,
            Authentication auth) {
        Long callerUserId = parseUserId(auth);
        LoanApplicationResponse result = loanService.reviewApplication(id, request, callerUserId);
        // 202 for approval (async disbursement in flight), 200 for rejection (synchronous + final)
        boolean approved = "APPROVED".equals(result.getStatus());
        return approved
                ? ResponseEntity.accepted().body(result)
                : ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LOAN_OFFICER', 'TELLER', 'ADMIN')")
    public ResponseEntity<List<RepaymentScheduleResponse>> getRepaymentSchedule(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.getRepaymentSchedule(id));
    }

    @GetMapping("/probe/account-instance")
    public ResponseEntity<Map<String, Object>> probeAccountServiceInstance(
            @RequestParam(defaultValue = "lb") String mode,
            @RequestParam(required = false) String directBaseUrl,
            @RequestParam Long accountId) {
        return ResponseEntity.ok(loanService.probeAccountServiceInstance(mode, directBaseUrl, accountId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Extracts the numeric user ID from the Spring Security {@link Authentication}
     * whose principal is set to the X-User-Id header value (a String) by
     * {@code HeaderAuthFilter}.
     */
    private Long parseUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.parseLong(auth.getPrincipal().toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
