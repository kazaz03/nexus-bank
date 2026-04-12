package com.nexusbank.loanservice.controller;

import com.nexusbank.loanservice.dto.request.LoanApplicationRequest;
import com.nexusbank.loanservice.dto.request.LoanReviewRequest;
import com.nexusbank.loanservice.dto.response.LoanApplicationResponse;
import com.nexusbank.loanservice.dto.response.RepaymentScheduleResponse;
import com.nexusbank.loanservice.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    public ResponseEntity<List<LoanApplicationResponse>> getAllApplications() {
        return ResponseEntity.ok(loanService.getAllApplications());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanApplicationResponse> getApplication(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.getApplication(id));
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
}
