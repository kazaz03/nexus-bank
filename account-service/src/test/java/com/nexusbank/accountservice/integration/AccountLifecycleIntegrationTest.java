package com.nexusbank.accountservice.integration;

import com.nexusbank.accountservice.dto.request.CreateAccountRequest;
import com.nexusbank.accountservice.dto.response.AccountResponse;
import com.nexusbank.accountservice.dto.response.BalanceResponse;
import com.nexusbank.accountservice.repository.AccountRepository;
import com.nexusbank.accountservice.repository.DebitCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the account lifecycle: create, read, balance, and close.
 *
 * Uses a real MySQL container (Testcontainers) and a full Spring Boot context
 * with a random port. No mocking — every layer from HTTP → service → JPA → DB
 * is exercised.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class AccountLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    DebitCardRepository debitCardRepository;

    @BeforeEach
    void cleanDatabase() {
        debitCardRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── Create account ────────────────────────────────────────────────────────

    @Test
    void createCheckingAccount_persistsToDatabase_andReturns201() {
        CreateAccountRequest request = checkingRequest(42L);

        ResponseEntity<AccountResponse> response = restTemplate.postForEntity(
                "/api/accounts", request, AccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AccountResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getCustomerId()).isEqualTo(42L);
        assertThat(body.getAccountType()).isEqualTo("CHECKING");
        assertThat(body.getCurrency()).isEqualTo("BAM");
        assertThat(body.getStatus()).isEqualTo("ACTIVE");
        assertThat(body.getIban()).matches("BA39\\d{16}");
        assertThat(body.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        // Verify the account actually landed in the DB
        assertThat(accountRepository.findById(body.getId())).isPresent();
    }

    @Test
    void createSavingsAccount_withInterestRate_persistsCorrectly() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCustomerId(10L);
        request.setAccountType("SAVINGS");
        request.setCurrency("EUR");
        request.setInterestRate(new BigDecimal("2.50"));

        ResponseEntity<AccountResponse> response = restTemplate.postForEntity(
                "/api/accounts", request, AccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AccountResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccountType()).isEqualTo("SAVINGS");
        assertThat(body.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void createAccount_withInvalidType_returns400() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCustomerId(1L);
        request.setAccountType("INVALID_TYPE");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/accounts", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createAccount_withNullCustomerId_returns400() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType("CHECKING");
        // customerId intentionally left null

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/accounts", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Read account ──────────────────────────────────────────────────────────

    @Test
    void getAccount_whenExists_returns200WithCorrectData() {
        AccountResponse created = createAccount(99L, "CHECKING", "BAM");

        ResponseEntity<AccountResponse> response = restTemplate.getForEntity(
                "/api/accounts/" + created.getId(), AccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AccountResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(created.getId());
        assertThat(body.getIban()).isEqualTo(created.getIban());
        assertThat(body.getCustomerId()).isEqualTo(99L);
    }

    @Test
    void getAccount_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/accounts/99999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAccountsByCustomer_returnsOnlyMatchingAccounts() {
        createAccount(100L, "CHECKING", "BAM");
        createAccount(100L, "SAVINGS", "BAM");
        createAccount(200L, "CHECKING", "BAM");  // different customer

        ResponseEntity<AccountResponse[]> response = restTemplate.getForEntity(
                "/api/accounts/customer/100", AccountResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    @Test
    void getBalance_forNewAccount_showsZeroBalance() {
        AccountResponse created = createAccount(55L, "CHECKING", "BAM");

        ResponseEntity<BalanceResponse> response = restTemplate.getForEntity(
                "/api/accounts/" + created.getId() + "/balance", BalanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BalanceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.getStatus()).isEqualTo("ACTIVE");
        assertThat(body.getIban()).isEqualTo(created.getIban());
    }

    // ── Close account ─────────────────────────────────────────────────────────

    @Test
    void closeAccount_withZeroBalance_updatesStatusToClosed() {
        AccountResponse created = createAccount(77L, "CHECKING", "BAM");

        ResponseEntity<AccountResponse> response = restTemplate.exchange(
                "/api/accounts/" + created.getId() + "/close?closedBy=1",
                HttpMethod.PATCH, HttpEntity.EMPTY, AccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void closeAccount_alreadyClosed_returns422() {
        AccountResponse created = createAccount(78L, "CHECKING", "BAM");
        // Close once
        restTemplate.exchange("/api/accounts/" + created.getId() + "/close",
                HttpMethod.PATCH, HttpEntity.EMPTY, String.class);

        // Close again → should fail
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/accounts/" + created.getId() + "/close",
                HttpMethod.PATCH, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── IBAN uniqueness ───────────────────────────────────────────────────────

    @Test
    void createMultipleAccounts_eachReceivesUniqueIban() {
        AccountResponse a1 = createAccount(1L, "CHECKING", "BAM");
        AccountResponse a2 = createAccount(2L, "CHECKING", "BAM");
        AccountResponse a3 = createAccount(3L, "CHECKING", "BAM");

        assertThat(List.of(a1.getIban(), a2.getIban(), a3.getIban()))
                .doesNotHaveDuplicates();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AccountResponse createAccount(Long customerId, String type, String currency) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCustomerId(customerId);
        request.setAccountType(type);
        request.setCurrency(currency);
        ResponseEntity<AccountResponse> resp = restTemplate.postForEntity(
                "/api/accounts", request, AccountResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private CreateAccountRequest checkingRequest(Long customerId) {
        CreateAccountRequest r = new CreateAccountRequest();
        r.setCustomerId(customerId);
        r.setAccountType("CHECKING");
        r.setCurrency("BAM");
        r.setOverdraftLimit(new BigDecimal("200.00"));
        return r;
    }
}
