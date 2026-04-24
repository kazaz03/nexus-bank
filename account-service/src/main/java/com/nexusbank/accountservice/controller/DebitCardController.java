package com.nexusbank.accountservice.controller;

import com.nexusbank.accountservice.dto.request.IssueDebitCardRequest;
import com.nexusbank.accountservice.dto.response.DebitCardResponse;
import com.nexusbank.accountservice.service.DebitCardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class DebitCardController {

    private final DebitCardService debitCardService;

    public DebitCardController(DebitCardService debitCardService) {
        this.debitCardService = debitCardService;
    }

    @PostMapping("/api/accounts/{accountId}/cards")
    public ResponseEntity<DebitCardResponse> issueCard(
            @PathVariable Long accountId,
            @RequestBody IssueDebitCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(debitCardService.issueCard(accountId, request));
    }

    @GetMapping("/api/accounts/{accountId}/cards")
    public ResponseEntity<List<DebitCardResponse>> getCardsByAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(debitCardService.getCardsByAccount(accountId));
    }

    @PatchMapping("/api/cards/{cardId}/activate")
    public ResponseEntity<DebitCardResponse> activateCard(@PathVariable Long cardId) {
        return ResponseEntity.ok(debitCardService.activateCard(cardId));
    }

    @PatchMapping("/api/cards/{cardId}/block")
    public ResponseEntity<DebitCardResponse> blockCard(@PathVariable Long cardId) {
        return ResponseEntity.ok(debitCardService.blockCard(cardId));
    }

    @PatchMapping("/api/cards/{cardId}/unblock")
    public ResponseEntity<DebitCardResponse> unblockCard(@PathVariable Long cardId) {
        return ResponseEntity.ok(debitCardService.unblockCard(cardId));
    }
}
