package com.nexusbank.transactionservice.integration;

import com.nexusbank.transactionservice.dto.response.ExchangeRateResponse;
import com.nexusbank.transactionservice.dto.response.TransactionResponse;
import com.nexusbank.transactionservice.model.ExchangeRate;
import com.nexusbank.transactionservice.model.Transaction;
import com.nexusbank.transactionservice.repository.ExchangeRateRepository;
import com.nexusbank.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the read-only parts of the transaction-service
 * that have no dependency on account-service:
 *
 *  - Exchange rate listing (GET /api/exchange-rates)
 *  - Single transaction lookup (GET /api/transactions/{id})
 *  - Paginated transaction history (GET /api/transactions/account/{id})
 *  - Account statement (GET /api/accounts/{id}/statement)
 *
 * All operations use a real MySQL container and the full Spring Boot context.
 * Account-service is NOT involved; AccountClient is left in its real form but
 * is never called by these endpoints.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class ExchangeRateIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ExchangeRateRepository exchangeRateRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        exchangeRateRepository.deleteAll();
    }

    // ── Exchange rates ────────────────────────────────────────────────────────

    @Test
    void getExchangeRates_whenNoneSeeded_returnsEmptyList() {
        ResponseEntity<ExchangeRateResponse[]> response = restTemplate.getForEntity(
                "/api/exchange-rates", ExchangeRateResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getExchangeRates_returnsAllSeededRates() {
        saveRate("BAM", "EUR", "0.511292");
        saveRate("BAM", "USD", "0.540000");
        saveRate("EUR", "BAM", "1.955830");

        ResponseEntity<ExchangeRateResponse[]> response = restTemplate.getForEntity(
                "/api/exchange-rates", ExchangeRateResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(3);
    }

    @Test
    void getExchangeRates_returnsCorrectCurrencyPairsAndRates() {
        saveRate("EUR", "BAM", "1.955830");

        ResponseEntity<ExchangeRateResponse[]> response = restTemplate.getForEntity(
                "/api/exchange-rates", ExchangeRateResponse[].class);

        ExchangeRateResponse rate = response.getBody()[0];
        assertThat(rate.getFromCurrency()).isEqualTo("EUR");
        assertThat(rate.getToCurrency()).isEqualTo("BAM");
        assertThat(rate.getRate()).isEqualByComparingTo(new BigDecimal("1.955830"));
    }

    // ── Single transaction lookup ─────────────────────────────────────────────

    @Test
    void getTransaction_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/transactions/99999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getTransaction_forSeededTransaction_returns200WithCorrectFields() {
        Transaction tx = saveTransaction(101L, Transaction.TransactionType.DEPOSIT,
                new BigDecimal("250.00"), "BAM");

        ResponseEntity<TransactionResponse> response = restTemplate.getForEntity(
                "/api/transactions/" + tx.getId(), TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransactionResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(tx.getId());
        assertThat(body.getAccountId()).isEqualTo(101L);
        assertThat(body.getType()).isEqualTo("DEPOSIT");
        assertThat(body.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(body.getCurrency()).isEqualTo("BAM");
        assertThat(body.getStatus()).isEqualTo("COMPLETED");
    }

    // ── Transaction history (paginated) ──────────────────────────────────────

    @Test
    void getTransactionHistory_forAccountWithNoTransactions_returnsEmptyPage() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/transactions/account/9999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"totalElements\":0");
    }

    @Test
    void getTransactionHistory_returnsOnlyTransactionsForRequestedAccount() {
        // Account 200 has 2 transactions, account 300 has 1
        saveTransaction(200L, Transaction.TransactionType.DEPOSIT, new BigDecimal("100"), "BAM");
        saveTransaction(200L, Transaction.TransactionType.WITHDRAWAL, new BigDecimal("50"), "BAM");
        saveTransaction(300L, Transaction.TransactionType.DEPOSIT, new BigDecimal("200"), "BAM");

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/transactions/account/200", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"totalElements\":2");
    }

    @Test
    void getTransactionHistory_withTypeFilter_returnsOnlyMatchingType() {
        saveTransaction(400L, Transaction.TransactionType.DEPOSIT, new BigDecimal("100"), "BAM");
        saveTransaction(400L, Transaction.TransactionType.WITHDRAWAL, new BigDecimal("50"), "BAM");

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/transactions/account/400?type=DEPOSIT", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"totalElements\":1");
        assertThat(response.getBody()).contains("DEPOSIT");
    }

    @Test
    void getTransactionHistory_withInvalidTypeFilter_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/transactions/account/400?type=BOGUS_TYPE", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Account statement ─────────────────────────────────────────────────────

    @Test
    void getStatement_forAccountWithTransactions_returnsAggregatedSummary() {
        saveTransaction(500L, Transaction.TransactionType.DEPOSIT, new BigDecimal("1000"), "BAM");
        saveTransaction(500L, Transaction.TransactionType.WITHDRAWAL, new BigDecimal("200"), "BAM");

        String from = LocalDateTime.now().minusDays(1).toString();
        String to   = LocalDateTime.now().plusDays(1).toString();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/accounts/500/statement?from=" + from + "&to=" + to, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The statement should reference account 500 and include both transactions
        assertThat(response.getBody()).contains("500");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ExchangeRate saveRate(String from, String to, String rate) {
        ExchangeRate er = new ExchangeRate();
        er.setFromCurrency(from);
        er.setToCurrency(to);
        er.setRate(new BigDecimal(rate));
        er.setValidFrom(LocalDate.now().minusDays(30));
        er.setValidTo(LocalDate.now().plusDays(30));
        return exchangeRateRepository.save(er);
    }

    private Transaction saveTransaction(Long accountId, Transaction.TransactionType type,
                                        BigDecimal amount, String currency) {
        Transaction tx = new Transaction();
        tx.setAccountId(accountId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setCurrency(currency);
        tx.setBalanceAfter(amount);
        tx.setReference("TEST-REF");
        tx.setCreatedAt(LocalDateTime.now());
        tx.setCreatedBy(1L);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        return transactionRepository.save(tx);
    }
}
