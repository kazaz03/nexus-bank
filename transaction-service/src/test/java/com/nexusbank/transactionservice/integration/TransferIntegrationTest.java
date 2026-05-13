package com.nexusbank.transactionservice.integration;

import com.nexusbank.transactionservice.client.AccountClient;
import com.nexusbank.transactionservice.client.dto.AccountView;
import com.nexusbank.transactionservice.client.dto.BalanceUpdateResult;
import com.nexusbank.transactionservice.dto.request.TransferRequest;
import com.nexusbank.transactionservice.dto.response.TransferResponse;
import com.nexusbank.transactionservice.exception.AccountServiceException;
import com.nexusbank.transactionservice.model.Transaction;
import com.nexusbank.transactionservice.repository.ExchangeRateRepository;
import com.nexusbank.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the money transfer flow.
 *
 * {@link AccountClient} is replaced with a Mockito mock ({@code @MockBean}) so
 * account-service does not need to be running. All other layers — HTTP binding,
 * {@code TransferService} orchestration, transaction persistence, response
 * mapping — run against a real MySQL container.
 *
 * Tests verify:
 *  - Successful transfer persists exactly two {@link Transaction} rows (DEBIT + CREDIT)
 *  - Business-rule validation is enforced before any account-service call
 *  - Credit failure triggers compensation and no records are persisted
 *  - All validation errors propagate as the correct HTTP status codes
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class TransferIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    ExchangeRateRepository exchangeRateRepository;

    @MockBean
    AccountClient accountClient;

    private static final String SOURCE_IBAN = "BA391000000000000001";
    private static final String TARGET_IBAN = "BA392000000000000002";
    private static final Long   SOURCE_ID   = 1L;
    private static final Long   TARGET_ID   = 2L;
    private static final Long   CUSTOMER_ID = 100L;

    @BeforeEach
    void resetDatabase() {
        transactionRepository.deleteAll();
        exchangeRateRepository.deleteAll();
    }

    // ── Successful transfer ───────────────────────────────────────────────────

    @Test
    void transfer_sameCurrency_returns201AndPersistsBothTransactionLegs() {
        stubHappyPath(new BigDecimal("1000.00"), new BigDecimal("500.00"),
                "BAM", "BAM", "ACTIVE", "ACTIVE");

        ResponseEntity<TransferResponse> response = restTemplate.postForEntity(
                "/api/transactions/transfer", validRequest("250.00"), TransferResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransferResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo("COMPLETED");
        assertThat(body.getSourceAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(body.getTargetAmount()).isEqualByComparingTo(new BigDecimal("250.00"));

        // Both the DEBIT (source) and CREDIT (target) rows must be in the DB
        List<Transaction> saved = transactionRepository.findAll();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(t -> t.getType().name())
                .containsExactlyInAnyOrder("TRANSFER", "TRANSFER");
        assertThat(saved).extracting(Transaction::getStatus)
                .allMatch(s -> s == Transaction.TransactionStatus.COMPLETED);
    }

    @Test
    void transfer_successful_storesSeparateDebitAndCreditRows() {
        stubHappyPath(new BigDecimal("500.00"), new BigDecimal("200.00"),
                "BAM", "BAM", "ACTIVE", "ACTIVE");

        restTemplate.postForEntity("/api/transactions/transfer",
                validRequest("100.00"), TransferResponse.class);

        List<Transaction> all = transactionRepository.findAll();
        assertThat(all).hasSize(2);

        // One row is the debit (source account), one is the credit (target account)
        assertThat(all.stream().anyMatch(t -> t.getAccountId().equals(SOURCE_ID))).isTrue();
        assertThat(all.stream().anyMatch(t -> t.getAccountId().equals(TARGET_ID))).isTrue();
    }

    // ── Pre-debit validation errors ───────────────────────────────────────────

    @Test
    void transfer_sourceEqualsTarget_returns400WithoutCallingAccountService() {
        TransferRequest request = validRequest("100.00");
        request.setTargetIban(SOURCE_IBAN);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transactions/transfer", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(accountClient, never()).getByIban(anyString(), eq(false));
    }

    @Test
    void transfer_withNullAmount_returns400() {
        TransferRequest request = validRequest(null);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transactions/transfer", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transfer_withZeroAmount_returns400() {
        TransferRequest request = validRequest("0.00");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transactions/transfer", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transfer_withInvalidIbanFormat_returns400() {
        TransferRequest request = validRequest("100.00");
        request.setSourceIban("INVALID");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transactions/transfer", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transfer_sourceAccountNotActive_returns400AndDoesNotPersistTransactions() {
        // IllegalArgumentException("Source account is not active") → mapped to 400 by GlobalExceptionHandler
        stubHappyPath(new BigDecimal("1000.00"), new BigDecimal("500.00"),
                "BAM", "BAM", "CLOSED", "ACTIVE");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transactions/transfer", validRequest("250.00"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void transfer_insufficientFunds_returns400AndDoesNotPersistTransactions() {
        // Source has 50, overdraftLimit 0, trying to transfer 250
        // IllegalArgumentException("Insufficient funds") → mapped to 400 by GlobalExceptionHandler
        stubHappyPath(new BigDecimal("50.00"), new BigDecimal("500.00"),
                "BAM", "BAM", "ACTIVE", "ACTIVE");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transactions/transfer", validRequest("250.00"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transactionRepository.count()).isZero();
    }

    // ── Compensation path ─────────────────────────────────────────────────────

    @Test
    void transfer_creditFailsAfterDebit_noTransactionsPersisted() {
        AccountView source = accountView(SOURCE_ID, SOURCE_IBAN, "BAM",
                new BigDecimal("1000"), "ACTIVE");
        AccountView target = accountView(TARGET_ID, TARGET_IBAN, "BAM",
                new BigDecimal("500"), "ACTIVE");

        when(accountClient.getByIban(SOURCE_IBAN, false)).thenReturn(source);
        when(accountClient.getByIban(TARGET_IBAN, false)).thenReturn(target);
        when(accountClient.debit(eq(SOURCE_ID), any(), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(SOURCE_ID, SOURCE_IBAN, "BAM",
                        new BigDecimal("750.00")));
        // Credit to target fails
        when(accountClient.credit(eq(TARGET_ID), any(), anyString(), anyString()))
                .thenThrow(new AccountServiceException("Account closed",
                        false, org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY));
        // Compensation credit to source succeeds
        when(accountClient.credit(eq(SOURCE_ID), any(), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(SOURCE_ID, SOURCE_IBAN, "BAM",
                        new BigDecimal("1000.00")));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transactions/transfer", validRequest("250.00"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // No transaction records must be persisted when the transfer is aborted
        assertThat(transactionRepository.count()).isZero();
        // Compensation credit must have been issued
        verify(accountClient).credit(eq(SOURCE_ID), any(), anyString(), anyString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TransferRequest validRequest(String amount) {
        TransferRequest r = new TransferRequest();
        r.setSourceIban(SOURCE_IBAN);
        r.setTargetIban(TARGET_IBAN);
        r.setAmount(amount != null ? new BigDecimal(amount) : null);
        r.setReference("Integration Test Transfer");
        r.setInitiatedBy(CUSTOMER_ID);
        return r;
    }

    private void stubHappyPath(BigDecimal sourceBalance, BigDecimal targetBalance,
                               String sourceCurrency, String targetCurrency,
                               String sourceStatus, String targetStatus) {
        AccountView source = accountView(SOURCE_ID, SOURCE_IBAN, sourceCurrency,
                sourceBalance, sourceStatus);
        AccountView target = accountView(TARGET_ID, TARGET_IBAN, targetCurrency,
                targetBalance, targetStatus);

        when(accountClient.getByIban(SOURCE_IBAN, false)).thenReturn(source);
        when(accountClient.getByIban(TARGET_IBAN, false)).thenReturn(target);

        when(accountClient.debit(eq(SOURCE_ID), any(), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(SOURCE_ID, SOURCE_IBAN,
                        sourceCurrency, sourceBalance.subtract(new BigDecimal("250.00"))));
        when(accountClient.credit(eq(TARGET_ID), any(), anyString(), anyString()))
                .thenReturn(new BalanceUpdateResult(TARGET_ID, TARGET_IBAN,
                        targetCurrency, targetBalance.add(new BigDecimal("250.00"))));
    }

    private AccountView accountView(Long id, String iban, String currency,
                                    BigDecimal balance, String status) {
        return new AccountView(id, CUSTOMER_ID, iban, "CHECKING", currency,
                balance, BigDecimal.ZERO, status);
    }
}
