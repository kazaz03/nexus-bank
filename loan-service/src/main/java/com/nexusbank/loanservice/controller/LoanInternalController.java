package com.nexusbank.loanservice.controller;

import com.nexusbank.loanservice.dto.response.LoanStatsResponse;
import com.nexusbank.loanservice.service.LoanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API used by other microservices via synchronous HTTP calls (not
 * routed through the public gateway). User Service calls this to aggregate
 * loan metrics for the admin dashboard (F17).
 */
@RestController
@RequestMapping("/api/loans/internal")
public class LoanInternalController {

    private final LoanService loanService;

    public LoanInternalController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/stats")
    public ResponseEntity<LoanStatsResponse> getStats() {
        return ResponseEntity.ok(loanService.getStats());
    }
}
