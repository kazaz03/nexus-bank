package com.nexusbank.transactionservice.controller;

import com.nexusbank.transactionservice.dto.request.CashTransactionRequest;
import com.nexusbank.transactionservice.dto.request.TransferRequest;
import com.nexusbank.transactionservice.dto.response.ExchangeRateResponse;
import com.nexusbank.transactionservice.dto.response.StatementResponse;
import com.nexusbank.transactionservice.dto.response.TransactionResponse;
import com.nexusbank.transactionservice.dto.response.TransferResponse;
import com.nexusbank.transactionservice.service.CashService;
import com.nexusbank.transactionservice.service.StatementPdfService;
import com.nexusbank.transactionservice.service.TransactionService;
import com.nexusbank.transactionservice.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransferService transferService;
    private final CashService cashService;
    private final StatementPdfService statementPdfService;

    public TransactionController(TransactionService transactionService,
                                 TransferService transferService,
                                 CashService cashService,
                                 StatementPdfService statementPdfService) {
        this.transactionService = transactionService;
        this.transferService = transferService;
        this.cashService = cashService;
        this.statementPdfService = statementPdfService;
    }

    @PostMapping("/transactions/transfer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request,
                                                     Authentication auth) {
        Long callerUserId = parseUserId(auth);
        TransferResponse response = transferService.transfer(request, callerUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** F10: teller deposits cash onto a customer's account. */
    @PostMapping("/transactions/deposit")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody CashTransactionRequest request,
                                                       Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashService.deposit(request, parseUserId(auth)));
    }

    /** F10: teller withdraws cash from a customer's account. */
    @PostMapping("/transactions/withdrawal")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<TransactionResponse> withdrawal(@Valid @RequestBody CashTransactionRequest request,
                                                          Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashService.withdraw(request, parseUserId(auth)));
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransaction(id));
    }

    @GetMapping("/transactions/account/{accountId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<Page<TransactionResponse>> getTransactionHistory(
            @PathVariable Long accountId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountId, type, from, to, pageable));
    }

    @GetMapping("/accounts/{accountId}/statement")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<StatementResponse> getStatement(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(transactionService.getStatement(accountId, from, to));
    }

    /** F16: server-side PDF export of an account statement. */
    @GetMapping("/transactions/accounts/{accountId}/statement/pdf")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<byte[]> getStatementPdf(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        StatementResponse statement = transactionService.getStatement(accountId, from, to);
        byte[] pdf = statementPdfService.generate(statement);

        String filename = "statement-account-" + accountId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @GetMapping("/exchange-rates")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'LOAN_OFFICER')")
    public ResponseEntity<List<ExchangeRateResponse>> getExchangeRates() {
        return ResponseEntity.ok(transactionService.getCurrentExchangeRates());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Extracts the numeric userId set as principal by HeaderAuthFilter (X-User-Id). */
    private Long parseUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        try {
            return Long.parseLong(auth.getPrincipal().toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
