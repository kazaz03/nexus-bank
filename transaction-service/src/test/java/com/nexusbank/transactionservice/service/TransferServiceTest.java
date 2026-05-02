package com.nexusbank.transactionservice.service;

import com.nexusbank.transactionservice.client.AccountClient;
import com.nexusbank.transactionservice.client.dto.AccountView;
import com.nexusbank.transactionservice.client.dto.BalanceUpdateResult;
import com.nexusbank.transactionservice.dto.request.TransferRequest;
import com.nexusbank.transactionservice.dto.response.TransferResponse;
import com.nexusbank.transactionservice.exception.AccountServiceException;
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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the F11 transfer orchestrator. AccountClient and the JPA
 * repositories are mocked so the test verifies pure orchestration logic
 * (validation, FX resolution, compensation) without making actual HTTP
 * calls or hitting a database.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountClient accountClient;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private TransferService transferService;

    private AccountView source;
    private AccountView target;

    @BeforeEach
    void setUp() {
        source = new AccountView(
                1L, 100L, "BA391000000000000001", "CHECKING", "BAM",
                BigDecimal.valueOf(1000), BigDecimal.ZERO, "ACTIVE");
        target = new AccountView(
                2L, 200L, "BA392000000000000002", "CHECKING", "BAM",
                BigDecimal.valueOf(500), BigDecimal.ZERO, "ACTIVE");
    }

    private TransferRequest validRequest() {
        TransferRequest request = new TransferRequest();
        request.setSourceIban(source.getIban());
        request.setTargetIban(target.getIban());
        request.setAmount(BigDecimal.valueOf(250));
        request.setReference("Rent March 2026");
        request.setInitiatedBy(source.getCustomerId());
        return request;
    }

    // ── Happy paths ──────────────────────────────────────────────────────────

    @Test
    void transfer_sameCurrency_appliesDebitThenCreditAndPersistsBothLegs() {
        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);

        BalanceUpdateResult debitResult = new BalanceUpdateResult(
                source.getId(), source.getIban(), "BAM", BigDecimal.valueOf(750));
        BalanceUpdateResult creditResult = new BalanceUpdateResult(
                target.getId(), target.getIban(), "BAM", BigDecimal.valueOf(750));

        when(accountClient.debit(eq(source.getId()), eq(BigDecimal.valueOf(250)), anyString(), anyString()))
                .thenReturn(debitResult);
        when(accountClient.credit(eq(target.getId()), eq(BigDecimal.valueOf(250)), anyString(), anyString()))
                .thenReturn(creditResult);

        TransferResponse result = transferService.transfer(validRequest());

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getSourceCurrency()).isEqualTo("BAM");
        assertThat(result.getTargetCurrency()).isEqualTo("BAM");
        assertThat(result.getSourceAmount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(result.getTargetAmount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(result.getExchangeRate()).isNull();
        assertThat(result.getSourceBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(750));

        // Verify two transactions saved (one DEBIT-side, one CREDIT-side).
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void transfer_crossCurrency_appliesExchangeRateToTargetAmount() {
        // Source EUR, target BAM — 1 EUR = 1.95583 BAM
        source.setCurrency("EUR");
        ExchangeRate rate = new ExchangeRate();
        rate.setId(7L);
        rate.setFromCurrency("EUR");
        rate.setToCurrency("BAM");
        rate.setRate(new BigDecimal("1.955830"));
        rate.setValidFrom(LocalDate.now().minusDays(1));
        rate.setValidTo(LocalDate.now().plusDays(1));

        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);
        when(exchangeRateRepository
                .findByFromCurrencyAndToCurrencyAndValidFromLessThanEqualAndValidToGreaterThanEqual(
                        eq("EUR"), eq("BAM"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(rate));

        when(accountClient.debit(anyLong(), any(BigDecimal.class), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(source.getId(), source.getIban(), "EUR", BigDecimal.valueOf(750)));
        when(accountClient.credit(anyLong(), any(BigDecimal.class), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(target.getId(), target.getIban(), "BAM", new BigDecimal("988.96")));

        TransferRequest request = validRequest();
        request.setAmount(new BigDecimal("250"));

        TransferResponse result = transferService.transfer(request);

        // 250 EUR * 1.955830 = 488.9575 → rounded HALF_UP to 488.96
        assertThat(result.getTargetAmount()).isEqualByComparingTo(new BigDecimal("488.96"));
        assertThat(result.getExchangeRate()).isEqualByComparingTo(new BigDecimal("1.955830"));
    }

    // ── Pre-debit validation failures ────────────────────────────────────────

    @Test
    void transfer_sourceEqualsTarget_throwsBeforeAnyHttpCall() {
        TransferRequest request = validRequest();
        request.setTargetIban(request.getSourceIban());

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source and target IBAN must differ");

        verify(accountClient, never()).getByIban(anyString(), eq(false));
    }

    @Test
    void transfer_sourceNotActive_throwsAndDoesNotDebit() {
        source.setStatus("CLOSED");
        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);

        assertThatThrownBy(() -> transferService.transfer(validRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source account is not active");

        verify(accountClient, never()).debit(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void transfer_targetNotActive_throwsAndDoesNotDebit() {
        target.setStatus("CLOSED");
        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);

        assertThatThrownBy(() -> transferService.transfer(validRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target account is not active");

        verify(accountClient, never()).debit(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void transfer_initiatorNotOwner_throws() {
        TransferRequest request = validRequest();
        request.setInitiatedBy(999L); // not the source account's customer

        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Initiator does not own");
    }

    @Test
    void transfer_insufficientFundsAtPreCheck_throwsBeforeDebit() {
        source.setBalance(BigDecimal.valueOf(100));
        source.setOverdraftLimit(BigDecimal.ZERO);
        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);

        TransferRequest request = validRequest();
        request.setAmount(BigDecimal.valueOf(250));

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");

        verify(accountClient, never()).debit(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void transfer_toSavingsAccountOfDifferentCustomer_throws() {
        target.setAccountType("SAVINGS");
        target.setCustomerId(999L); // different owner
        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);

        assertThatThrownBy(() -> transferService.transfer(validRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SAVINGS account are restricted");
    }

    @Test
    void transfer_noActiveExchangeRate_throws() {
        source.setCurrency("USD");
        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);
        when(exchangeRateRepository
                .findByFromCurrencyAndToCurrencyAndValidFromLessThanEqualAndValidToGreaterThanEqual(
                        eq("USD"), eq("BAM"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of()); // no rate found

        assertThatThrownBy(() -> transferService.transfer(validRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active exchange rate");

        verify(accountClient, never()).debit(anyLong(), any(), anyString(), anyString());
    }

    // ── Failures during the inter-service calls ──────────────────────────────

    @Test
    void transfer_debitFailsAtAccountService_throwsAndDoesNotCompensate() {
        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);
        when(accountClient.debit(anyLong(), any(), anyString(), anyString()))
                .thenThrow(new AccountServiceException("Insufficient funds", false, HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> transferService.transfer(validRequest()))
                .isInstanceOf(AccountServiceException.class);

        // No credit attempt, no compensation, no persistence.
        verify(accountClient, never()).credit(anyLong(), any(), anyString(), anyString());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_creditFailsAfterDebit_triggersCompensationRefund() {
        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);

        when(accountClient.debit(eq(source.getId()), any(), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(source.getId(), source.getIban(), "BAM", BigDecimal.valueOf(750)));

        // First credit (target leg) fails, second credit (refund) succeeds.
        when(accountClient.credit(eq(target.getId()), any(), anyString(), anyString()))
                .thenThrow(new AccountServiceException("Account closed", false, HttpStatus.UNPROCESSABLE_ENTITY));
        when(accountClient.credit(eq(source.getId()), any(), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(source.getId(), source.getIban(), "BAM", BigDecimal.valueOf(1000)));

        assertThatThrownBy(() -> transferService.transfer(validRequest()))
                .isInstanceOf(AccountServiceException.class)
                .hasMessageContaining("source account refunded");

        // Compensation credit issued to source.
        verify(accountClient).credit(eq(source.getId()), any(), anyString(), anyString());
        // No transaction records persisted (transfer aborted).
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_creditFailsWith5xx_propagatesAsRetryable() {
        when(accountClient.getByIban(source.getIban(), false)).thenReturn(source);
        when(accountClient.getByIban(target.getIban(), false)).thenReturn(target);
        when(accountClient.debit(anyLong(), any(), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(source.getId(), source.getIban(), "BAM", BigDecimal.valueOf(750)));
        when(accountClient.credit(eq(target.getId()), any(), anyString(), anyString()))
                .thenThrow(new AccountServiceException("Account Service unreachable", true, HttpStatus.SERVICE_UNAVAILABLE));
        when(accountClient.credit(eq(source.getId()), any(), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(source.getId(), source.getIban(), "BAM", BigDecimal.valueOf(1000)));

        assertThatThrownBy(() -> transferService.transfer(validRequest()))
                .isInstanceOf(AccountServiceException.class)
                .matches(ex -> ((AccountServiceException) ex).isRetryable(),
                        "exception should be marked retryable");
    }
}
