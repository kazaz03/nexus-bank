package com.nexusbank.accountservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.accountservice.dto.request.IssueDebitCardRequest;
import com.nexusbank.accountservice.dto.response.DebitCardResponse;
import com.nexusbank.accountservice.exception.AccountOperationException;
import com.nexusbank.accountservice.exception.GlobalExceptionHandler;
import com.nexusbank.accountservice.exception.ResourceNotFoundException;
import com.nexusbank.accountservice.service.DebitCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DebitCardController.class)
@Import(GlobalExceptionHandler.class)
class DebitCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DebitCardService debitCardService;

    @Autowired
    private ObjectMapper objectMapper;

    private DebitCardResponse sampleCardResponse;

    @BeforeEach
    void setUp() {
        sampleCardResponse = new DebitCardResponse();
        sampleCardResponse.setId(10L);
        sampleCardResponse.setAccountId(1L);
        sampleCardResponse.setMaskedCardNumber("**** **** **** 1234");
        sampleCardResponse.setExpiryDate(LocalDate.now().plusYears(3));
        sampleCardResponse.setStatus("PENDING");
        sampleCardResponse.setIssuedAt(LocalDateTime.now());
    }

    // ── POST /api/accounts/{accountId}/cards ─────────────────────────────────

    @Test
    void issueCard_withValidRequest_returns201() throws Exception {
        IssueDebitCardRequest request = new IssueDebitCardRequest();
        request.setIssuedBy(5L);

        when(debitCardService.issueCard(eq(1L), any(IssueDebitCardRequest.class)))
                .thenReturn(sampleCardResponse);

        mockMvc.perform(post("/api/accounts/1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void issueCard_forAccountWithExistingCard_returns422() throws Exception {
        when(debitCardService.issueCard(eq(1L), any()))
                .thenThrow(new AccountOperationException("Account already has an active or pending debit card"));

        mockMvc.perform(post("/api/accounts/1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void issueCard_forNonExistentAccount_returns404() throws Exception {
        when(debitCardService.issueCard(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Account not found: 99"));

        mockMvc.perform(post("/api/accounts/99/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account not found: 99"));
    }

    // ── GET /api/accounts/{accountId}/cards ──────────────────────────────────

    @Test
    void getCardsByAccount_returns200WithList() throws Exception {
        when(debitCardService.getCardsByAccount(1L)).thenReturn(List.of(sampleCardResponse));

        mockMvc.perform(get("/api/accounts/1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(1))
                .andExpect(jsonPath("$[0].maskedCardNumber").value("**** **** **** 1234"));
    }

    @Test
    void getCardsByAccount_whenAccountNotFound_returns404() throws Exception {
        when(debitCardService.getCardsByAccount(99L))
                .thenThrow(new ResourceNotFoundException("Account not found: 99"));

        mockMvc.perform(get("/api/accounts/99/cards"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ── PATCH /api/cards/{cardId}/activate ───────────────────────────────────

    @Test
    void activateCard_whenPending_returns200() throws Exception {
        DebitCardResponse activated = new DebitCardResponse();
        activated.setId(10L);
        activated.setStatus("ACTIVE");

        when(debitCardService.activateCard(10L)).thenReturn(activated);

        mockMvc.perform(patch("/api/cards/10/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void activateCard_whenAlreadyActive_returns422() throws Exception {
        when(debitCardService.activateCard(10L))
                .thenThrow(new AccountOperationException("Only PENDING cards can be activated"));

        mockMvc.perform(patch("/api/cards/10/activate"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── PATCH /api/cards/{cardId}/block ──────────────────────────────────────

    @Test
    void blockCard_whenActive_returns200() throws Exception {
        DebitCardResponse blocked = new DebitCardResponse();
        blocked.setId(10L);
        blocked.setStatus("BLOCKED");

        when(debitCardService.blockCard(10L)).thenReturn(blocked);

        mockMvc.perform(patch("/api/cards/10/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    // ── PATCH /api/cards/{cardId}/unblock ────────────────────────────────────

    @Test
    void unblockCard_whenBlocked_returns200() throws Exception {
        DebitCardResponse unblocked = new DebitCardResponse();
        unblocked.setId(10L);
        unblocked.setStatus("ACTIVE");

        when(debitCardService.unblockCard(10L)).thenReturn(unblocked);

        mockMvc.perform(patch("/api/cards/10/unblock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
