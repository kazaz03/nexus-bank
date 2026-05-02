package com.nexusbank.accountservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.accountservice.dto.request.BalanceUpdateRequest;
import com.nexusbank.accountservice.dto.response.AccountInternalResponse;
import com.nexusbank.accountservice.dto.response.BalanceUpdateResponse;
import com.nexusbank.accountservice.exception.AccountOperationException;
import com.nexusbank.accountservice.exception.GlobalExceptionHandler;
import com.nexusbank.accountservice.exception.ResourceNotFoundException;
import com.nexusbank.accountservice.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for {@link AccountInternalController} — the API surface
 * other microservices (Transaction Service, Loan Service) hit during
 * synchronous inter-service calls.
 */
@WebMvcTest(AccountInternalController.class)
@Import(GlobalExceptionHandler.class)
class AccountInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    // ── GET /api/accounts/internal/by-iban/{iban} ────────────────────────────

    @Test
    void getByIban_whenFound_returns200WithAccountView() throws Exception {
        AccountInternalResponse stubbed = new AccountInternalResponse(
                1L, 100L, "BA391000000000000001", "CHECKING", "BAM",
                BigDecimal.valueOf(1000), BigDecimal.ZERO, "ACTIVE");
        when(accountService.getInternalByIban("BA391000000000000001")).thenReturn(stubbed);

        mockMvc.perform(get("/api/accounts/internal/by-iban/BA391000000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountType").value("CHECKING"))
                .andExpect(jsonPath("$.balance").value(1000));
    }

    @Test
    void getByIban_whenNotFound_returns404WithErrorShape() throws Exception {
        when(accountService.getInternalByIban("BAD-IBAN"))
                .thenThrow(new ResourceNotFoundException("Account not found for IBAN: BAD-IBAN"));

        mockMvc.perform(get("/api/accounts/internal/by-iban/BAD-IBAN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account not found for IBAN: BAD-IBAN"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/accounts/internal/{id} ───────────────────────────────────────

    @Test
    void getById_whenFound_returns200() throws Exception {
        AccountInternalResponse stubbed = new AccountInternalResponse(
                1L, 100L, "BA391000000000000001", "CHECKING", "BAM",
                BigDecimal.valueOf(1000), BigDecimal.ZERO, "ACTIVE");
        when(accountService.getInternalById(1L)).thenReturn(stubbed);

        mockMvc.perform(get("/api/accounts/internal/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── POST /api/accounts/internal/{id}/debit ────────────────────────────────

    @Test
    void debit_validRequest_returns200WithNewBalance() throws Exception {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setAmount(BigDecimal.valueOf(250));
        request.setReference("TRX-001");
        request.setIdempotencyKey("TRX-001-debit");

        BalanceUpdateResponse stubbed = new BalanceUpdateResponse(
                1L, "BA391000000000000001", "BAM", BigDecimal.valueOf(750));
        when(accountService.debit(eq(1L), any(BalanceUpdateRequest.class))).thenReturn(stubbed);

        mockMvc.perform(post("/api/accounts/internal/1/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.newBalance").value(750));
    }

    @Test
    void debit_insufficientFunds_returns422() throws Exception {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setAmount(BigDecimal.valueOf(99999));
        request.setReference("TRX-002");

        when(accountService.debit(eq(1L), any(BalanceUpdateRequest.class)))
                .thenThrow(new AccountOperationException("Insufficient funds on account BA39..."));

        mockMvc.perform(post("/api/accounts/internal/1/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Insufficient funds on account BA39..."));
    }

    @Test
    void debit_zeroAmount_returns400FromBeanValidation() throws Exception {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setAmount(BigDecimal.ZERO); // violates @DecimalMin("0.01")
        request.setReference("TRX-003");

        mockMvc.perform(post("/api/accounts/internal/1/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void debit_missingAmount_returns400FromBeanValidation() throws Exception {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        // amount intentionally null

        mockMvc.perform(post("/api/accounts/internal/1/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    // ── POST /api/accounts/internal/{id}/credit ───────────────────────────────

    @Test
    void credit_validRequest_returns200() throws Exception {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setAmount(BigDecimal.valueOf(250));
        request.setReference("TRX-001");

        BalanceUpdateResponse stubbed = new BalanceUpdateResponse(
                2L, "BA392000000000000002", "BAM", BigDecimal.valueOf(750));
        when(accountService.credit(eq(2L), any(BalanceUpdateRequest.class))).thenReturn(stubbed);

        mockMvc.perform(post("/api/accounts/internal/2/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newBalance").value(750));
    }

    @Test
    void credit_closedAccount_returns422() throws Exception {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setAmount(BigDecimal.valueOf(100));
        request.setReference("TRX-004");

        when(accountService.credit(eq(99L), any(BalanceUpdateRequest.class)))
                .thenThrow(new AccountOperationException("Account is not active (status=CLOSED)"));

        mockMvc.perform(post("/api/accounts/internal/99/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Account is not active (status=CLOSED)"));
    }
}
