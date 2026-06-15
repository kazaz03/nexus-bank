package com.nexusbank.transactionservice.controller;

import com.nexusbank.transactionservice.dto.response.TransactionStatsResponse;
import com.nexusbank.transactionservice.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API surface used by other microservices via synchronous HTTP
 * calls (not routed through the public gateway). User Service calls this to
 * aggregate transaction metrics for the admin dashboard (F17).
 */
@RestController
@RequestMapping("/api/transactions/internal")
public class TransactionInternalController {

    private final TransactionService transactionService;

    public TransactionInternalController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/stats")
    public ResponseEntity<TransactionStatsResponse> getStats() {
        return ResponseEntity.ok(transactionService.getStats());
    }
}
