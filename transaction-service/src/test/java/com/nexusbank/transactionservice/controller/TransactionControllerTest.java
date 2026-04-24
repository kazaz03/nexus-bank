package com.nexusbank.transactionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.transactionservice.dto.response.ExchangeRateResponse;
import com.nexusbank.transactionservice.dto.response.StatementResponse;
import com.nexusbank.transactionservice.dto.response.TransactionResponse;
import com.nexusbank.transactionservice.exception.GlobalExceptionHandler;
import com.nexusbank.transactionservice.exception.ResourceNotFoundException;
import com.nexusbank.transactionservice.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Autowired
    private ObjectMapper objectMapper;

    private TransactionResponse sampleTx;

    @BeforeEach
    void setUp() {
        sampleTx = new TransactionResponse();
        sampleTx.setId(1L);
        sampleTx.setAccountId(10L);
        sampleTx.setType("DEPOSIT");
        sampleTx.setAmount(BigDecimal.valueOf(200));
        sampleTx.setCurrency("BAM");
        sampleTx.setBalanceAfter(BigDecimal.valueOf(700));
        sampleTx.setStatus("COMPLETED");
        sampleTx.setCreatedAt(LocalDateTime.now());
    }

    // ── GET /api/transactions/{id} ────────────────────────────────────────────

    @Test
    void getTransaction_whenFound_returns200() throws Exception {
        when(transactionService.getTransaction(1L)).thenReturn(sampleTx);

        mockMvc.perform(get("/api/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.currency").value("BAM"));
    }

    @Test
    void getTransaction_whenNotFound_returns404WithErrorShape() throws Exception {
        when(transactionService.getTransaction(99L))
                .thenThrow(new ResourceNotFoundException("Transaction not found: 99"));

        mockMvc.perform(get("/api/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Transaction not found: 99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/transactions/account/{accountId} ─────────────────────────────

    @Test
    void getTransactionHistory_withNoFilters_returns200WithPage() throws Exception {
        when(transactionService.getTransactionHistory(
                eq(10L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleTx)));

        mockMvc.perform(get("/api/transactions/account/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountId").value(10))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"));
    }

    @Test
    void getTransactionHistory_withTypeFilter_returns200() throws Exception {
        when(transactionService.getTransactionHistory(
                eq(10L), eq("DEPOSIT"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleTx)));

        mockMvc.perform(get("/api/transactions/account/10").param("type", "DEPOSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getTransactionHistory_withInvalidType_returns400() throws Exception {
        when(transactionService.getTransactionHistory(
                eq(10L), eq("BOGUS"), isNull(), isNull(), any(Pageable.class)))
                .thenThrow(new IllegalArgumentException("Invalid transaction type: BOGUS"));

        mockMvc.perform(get("/api/transactions/account/10").param("type", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid transaction type: BOGUS"));
    }

    // ── GET /api/accounts/{accountId}/statement ───────────────────────────────

    @Test
    void getStatement_withValidDateRange_returns200() throws Exception {
        StatementResponse statement = new StatementResponse();
        statement.setAccountId(10L);
        statement.setTotalDeposited(BigDecimal.valueOf(200));
        statement.setTotalWithdrawn(BigDecimal.ZERO);
        statement.setClosingBalance(BigDecimal.valueOf(700));
        statement.setTransactions(List.of(sampleTx));

        when(transactionService.getStatement(eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(statement);

        mockMvc.perform(get("/api/accounts/10/statement")
                        .param("from", "2026-01-01T00:00:00")
                        .param("to", "2026-04-15T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(10))
                .andExpect(jsonPath("$.totalDeposited").value(200));
    }

    // ── GET /api/exchange-rates ───────────────────────────────────────────────

    @Test
    void getExchangeRates_returns200WithList() throws Exception {
        ExchangeRateResponse rate = new ExchangeRateResponse();
        rate.setFromCurrency("BAM");
        rate.setToCurrency("EUR");
        rate.setRate(BigDecimal.valueOf(0.51));

        when(transactionService.getCurrentExchangeRates()).thenReturn(List.of(rate));

        mockMvc.perform(get("/api/exchange-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromCurrency").value("BAM"))
                .andExpect(jsonPath("$[0].toCurrency").value("EUR"))
                .andExpect(jsonPath("$[0].rate").value(0.51));
    }

    @Test
    void getExchangeRates_whenEmpty_returns200WithEmptyList() throws Exception {
        when(transactionService.getCurrentExchangeRates()).thenReturn(List.of());

        mockMvc.perform(get("/api/exchange-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
