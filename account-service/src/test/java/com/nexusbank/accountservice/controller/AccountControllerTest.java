package com.nexusbank.accountservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.accountservice.dto.request.CreateAccountRequest;
import com.nexusbank.accountservice.dto.response.AccountResponse;
import com.nexusbank.accountservice.dto.response.BalanceResponse;
import com.nexusbank.accountservice.exception.AccountOperationException;
import com.nexusbank.accountservice.exception.GlobalExceptionHandler;
import com.nexusbank.accountservice.exception.ResourceNotFoundException;
import com.nexusbank.accountservice.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    private AccountResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new AccountResponse();
        sampleResponse.setId(1L);
        sampleResponse.setCustomerId(10L);
        sampleResponse.setIban("BA391234567890123456");
        sampleResponse.setAccountType("CHECKING");
        sampleResponse.setCurrency("BAM");
        sampleResponse.setBalance(BigDecimal.valueOf(500));
        sampleResponse.setOverdraftLimit(BigDecimal.ZERO);
        sampleResponse.setStatus("ACTIVE");
        sampleResponse.setCreatedAt(LocalDateTime.now());
    }

    // ── GET /api/accounts/{id} ────────────────────────────────────────────────

    @Test
    void getAccount_whenFound_returns200WithBody() throws Exception {
        when(accountService.getAccount(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.iban").value("BA391234567890123456"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getAccount_whenNotFound_returns404WithErrorShape() throws Exception {
        when(accountService.getAccount(99L))
                .thenThrow(new ResourceNotFoundException("Account not found: 99"));

        mockMvc.perform(get("/api/accounts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account not found: 99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/accounts/customer/{customerId} ───────────────────────────────

    @Test
    void getAccountsByCustomer_returnsList() throws Exception {
        when(accountService.getAccountsByCustomerId(10L)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/accounts/customer/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(10));
    }

    // ── GET /api/accounts/{id}/balance ────────────────────────────────────────

    @Test
    void getBalance_whenFound_returnsBalanceResponse() throws Exception {
        BalanceResponse balanceResponse = new BalanceResponse(
                1L, "BA391234567890123456", "BAM",
                BigDecimal.valueOf(500), BigDecimal.valueOf(200),
                BigDecimal.valueOf(700), "ACTIVE");

        when(accountService.getBalance(1L)).thenReturn(balanceResponse);

        mockMvc.perform(get("/api/accounts/1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500))
                .andExpect(jsonPath("$.availableBalance").value(700));
    }

    // ── POST /api/accounts ────────────────────────────────────────────────────

    @Test
    void createAccount_withValidRequest_returns201() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCustomerId(10L);
        request.setAccountType("CHECKING");

        when(accountService.createAccount(any(CreateAccountRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createAccount_withMissingCustomerId_returns400WithFieldErrors() throws Exception {
        // customerId is @NotNull — omitting it triggers validation failure
        String body = "{\"accountType\":\"CHECKING\"}";

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists())
                .andExpect(jsonPath("$.fieldErrors.customerId").exists());
    }

    @Test
    void createAccount_withInvalidAccountType_returns400() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCustomerId(10L);
        request.setAccountType("BOGUS");

        when(accountService.createAccount(any())).thenThrow(new IllegalArgumentException("Invalid account type: BOGUS"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid account type: BOGUS"));
    }

    // ── PATCH /api/accounts/{id}/close ────────────────────────────────────────

    @Test
    void closeAccount_whenValid_returns200() throws Exception {
        AccountResponse closedResponse = new AccountResponse();
        closedResponse.setId(1L);
        closedResponse.setStatus("CLOSED");

        when(accountService.closeAccount(eq(1L), any())).thenReturn(closedResponse);

        mockMvc.perform(patch("/api/accounts/1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void closeAccount_withNonZeroBalance_returns422() throws Exception {
        when(accountService.closeAccount(eq(1L), any()))
                .thenThrow(new AccountOperationException("Cannot close account with non-zero balance"));

        mockMvc.perform(patch("/api/accounts/1/close"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }
}
