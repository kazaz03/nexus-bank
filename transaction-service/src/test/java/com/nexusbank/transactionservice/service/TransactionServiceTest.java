package com.nexusbank.transactionservice.service;

import com.nexusbank.transactionservice.dto.response.ExchangeRateResponse;
import com.nexusbank.transactionservice.dto.response.TransactionResponse;
import com.nexusbank.transactionservice.exception.ResourceNotFoundException;
import com.nexusbank.transactionservice.model.ExchangeRate;
import com.nexusbank.transactionservice.model.Transaction;
import com.nexusbank.transactionservice.repository.ExchangeRateRepository;
import com.nexusbank.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction sampleTransaction;
    private TransactionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleTransaction = new Transaction();
        sampleTransaction.setId(1L);
        sampleTransaction.setAccountId(10L);
        sampleTransaction.setType(Transaction.TransactionType.DEPOSIT);
        sampleTransaction.setAmount(BigDecimal.valueOf(200));
        sampleTransaction.setCurrency("BAM");
        sampleTransaction.setBalanceAfter(BigDecimal.valueOf(700));
        sampleTransaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        sampleTransaction.setCreatedAt(LocalDateTime.now());

        sampleResponse = new TransactionResponse();
        sampleResponse.setId(1L);
        sampleResponse.setAccountId(10L);
        sampleResponse.setType("DEPOSIT");
        sampleResponse.setAmount(BigDecimal.valueOf(200));
        sampleResponse.setCurrency("BAM");
        sampleResponse.setStatus("COMPLETED");
    }

    // ── getTransaction ────────────────────────────────────────────────────────

    @Test
    void getTransaction_whenFound_returnsResponse() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(sampleTransaction));

        TransactionResponse result = transactionService.getTransaction(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getType()).isEqualTo("DEPOSIT");
        verify(transactionRepository).findById(1L);
    }

    @Test
    void getTransaction_whenNotFound_throwsResourceNotFoundException() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.getTransaction(99L));
    }

    // ── getTransactionHistory – no filters ───────────────────────────────────

    @Test
    void getTransactionHistory_withNoFilters_delegatesToFindByAccountId() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Transaction> page = new PageImpl<>(List.of(sampleTransaction));

        when(transactionRepository.findByAccountId(10L, pageable)).thenReturn(page);

        Page<TransactionResponse> result = transactionService
                .getTransactionHistory(10L, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(transactionRepository).findByAccountId(10L, pageable);
    }

    @Test
    void getTransactionHistory_withTypeFilter_delegatesToFindByAccountIdAndType() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Transaction> page = new PageImpl<>(List.of(sampleTransaction));

        when(transactionRepository.findByAccountIdAndType(
                eq(10L), eq(Transaction.TransactionType.DEPOSIT), eq(pageable))).thenReturn(page);

        Page<TransactionResponse> result = transactionService
                .getTransactionHistory(10L, "DEPOSIT", null, null, pageable);

        assertThat(result).isNotNull();
        verify(transactionRepository).findByAccountIdAndType(10L, Transaction.TransactionType.DEPOSIT, pageable);
    }

    @Test
    void getTransactionHistory_withInvalidType_throwsIllegalArgumentException() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.getTransactionHistory(10L, "BOGUS_TYPE", null, null, pageable));
    }

    // ── getTransactionHistory – date range ───────────────────────────────────

    @Test
    void getTransactionHistory_withDateRange_delegatesToFindByAccountIdAndCreatedAtBetween() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        Page<Transaction> page = new PageImpl<>(List.of(sampleTransaction));

        when(transactionRepository.findByAccountIdAndCreatedAtBetween(10L, from, to, pageable))
                .thenReturn(page);

        Page<TransactionResponse> result = transactionService
                .getTransactionHistory(10L, null, from, to, pageable);

        assertThat(result).isNotNull();
        verify(transactionRepository).findByAccountIdAndCreatedAtBetween(10L, from, to, pageable);
    }

    // ── getCurrentExchangeRates ───────────────────────────────────────────────

    @Test
    void getCurrentExchangeRates_returnsAllRates() {
        ExchangeRate rate = new ExchangeRate();
        rate.setId(1L);
        rate.setFromCurrency("BAM");
        rate.setToCurrency("EUR");
        rate.setRate(BigDecimal.valueOf(0.51));

        ExchangeRateResponse rateResponse = new ExchangeRateResponse();
        rateResponse.setFromCurrency("BAM");
        rateResponse.setToCurrency("EUR");

        when(exchangeRateRepository.findAll()).thenReturn(List.of(rate));
        when(modelMapper.map(rate, ExchangeRateResponse.class)).thenReturn(rateResponse);

        List<ExchangeRateResponse> result = transactionService.getCurrentExchangeRates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFromCurrency()).isEqualTo("BAM");
    }

    // ── FX transaction – null exchangeRate (non-FX) ──────────────────────────

    @Test
    void getTransaction_withNullExchangeRate_returnsNullExchangeRateId() {
        sampleTransaction.setExchangeRate(null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(sampleTransaction));

        TransactionResponse result = transactionService.getTransaction(1L);

        assertThat(result.getExchangeRateId()).isNull();
    }
}
