package com.nexusbank.accountservice.integration;

import com.nexusbank.accountservice.dto.request.BalanceUpdateRequest;
import com.nexusbank.accountservice.dto.request.CreateAccountRequest;
import com.nexusbank.accountservice.dto.response.AccountInternalResponse;
import com.nexusbank.accountservice.dto.response.AccountResponse;
import com.nexusbank.accountservice.dto.response.BalanceUpdateResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the internal account API used by other microservices:
 * credit, debit, IBAN lookup, and balance consistency.
 *
 * All operations go through the full HTTP → service → JPA → MySQL stack.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class AccountInternalApiIntegrationTest {

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

    // ── Lookup by IBAN ────────────────────────────────────────────────────────

    @Test
    void getByIban_forExistingAccount_returnsAccountView() {
        AccountResponse created = createAccount(1L);

        ResponseEntity<AccountInternalResponse> response = restTemplate.getForEntity(
                "/api/accounts/internal/by-iban/" + created.getIban(),
                AccountInternalResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AccountInternalResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getIban()).isEqualTo(created.getIban());
        assertThat(body.getCustomerId()).isEqualTo(1L);
        assertThat(body.getStatus()).isEqualTo("ACTIVE");
        assertThat(body.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getByIban_forUnknownIban_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/accounts/internal/by-iban/BA390000000000000000",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getById_forExistingAccount_returnsAccountView() {
        AccountResponse created = createAccount(2L);

        ResponseEntity<AccountInternalResponse> response = restTemplate.getForEntity(
                "/api/accounts/internal/" + created.getId(),
                AccountInternalResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(created.getId());
    }

    // ── Credit ────────────────────────────────────────────────────────────────

    @Test
    void credit_increasesBalanceByExactAmount() {
        AccountResponse created = createAccount(10L);

        BalanceUpdateResponse result = credit(created.getId(), new BigDecimal("500.00"));

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(result.getIban()).isEqualTo(created.getIban());
    }

    @Test
    void credit_appliedTwice_accumulatesBalance() {
        AccountResponse created = createAccount(11L);

        credit(created.getId(), new BigDecimal("300.00"));
        BalanceUpdateResponse second = credit(created.getId(), new BigDecimal("200.00"));

        assertThat(second.getNewBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void credit_withZeroAmount_returns400() {
        AccountResponse created = createAccount(12L);

        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setReference("test");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/accounts/internal/" + created.getId() + "/credit",
                request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Debit ─────────────────────────────────────────────────────────────────

    @Test
    void debit_reducesBalanceByExactAmount() {
        AccountResponse created = createAccount(20L);
        credit(created.getId(), new BigDecimal("1000.00"));

        BalanceUpdateResponse result = debit(created.getId(), new BigDecimal("300.00"));

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    void debit_withInsufficientFunds_returns422() {
        AccountResponse created = createAccount(21L);
        // Balance is 0, overdraftLimit is 0 — any debit must fail

        ResponseEntity<String> response = debitRaw(created.getId(), new BigDecimal("100.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void debit_withOverdraft_succeedsUpToLimit() {
        // Create account with overdraft limit
        CreateAccountRequest req = new CreateAccountRequest();
        req.setCustomerId(22L);
        req.setAccountType("CHECKING");
        req.setCurrency("BAM");
        req.setOverdraftLimit(new BigDecimal("500.00"));
        AccountResponse created = restTemplate.postForEntity(
                "/api/accounts", req, AccountResponse.class).getBody();

        // Balance=0, overdraftLimit=500 → can debit up to 500
        BalanceUpdateResponse result = debit(created.getId(), new BigDecimal("250.00"));

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("-250.00"));
    }

    @Test
    void debit_exceedingOverdraftLimit_returns422() {
        CreateAccountRequest req = new CreateAccountRequest();
        req.setCustomerId(23L);
        req.setAccountType("CHECKING");
        req.setCurrency("BAM");
        req.setOverdraftLimit(new BigDecimal("100.00"));
        AccountResponse created = restTemplate.postForEntity(
                "/api/accounts", req, AccountResponse.class).getBody();

        // Balance=0, overdraftLimit=100 → debit 150 must fail
        ResponseEntity<String> response = debitRaw(created.getId(), new BigDecimal("150.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── Close + debit guard ───────────────────────────────────────────────────

    @Test
    void debit_fromClosedAccount_returns422() {
        AccountResponse created = createAccount(30L);
        // Close the account (balance is zero so it's allowed)
        restTemplate.exchange("/api/accounts/" + created.getId() + "/close",
                HttpMethod.PATCH, HttpEntity.EMPTY, String.class);

        ResponseEntity<String> response = debitRaw(created.getId(), new BigDecimal("1.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void closeAccount_withNonZeroBalance_returns422() {
        AccountResponse created = createAccount(31L);
        credit(created.getId(), new BigDecimal("100.00"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/accounts/" + created.getId() + "/close",
                HttpMethod.PATCH, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AccountResponse createAccount(Long customerId) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCustomerId(customerId);
        request.setAccountType("CHECKING");
        request.setCurrency("BAM");
        ResponseEntity<AccountResponse> resp = restTemplate.postForEntity(
                "/api/accounts", request, AccountResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private BalanceUpdateResponse credit(Long accountId, BigDecimal amount) {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setAmount(amount);
        request.setReference("test-credit");
        ResponseEntity<BalanceUpdateResponse> resp = restTemplate.postForEntity(
                "/api/accounts/internal/" + accountId + "/credit",
                request, BalanceUpdateResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private BalanceUpdateResponse debit(Long accountId, BigDecimal amount) {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setAmount(amount);
        request.setReference("test-debit");
        ResponseEntity<BalanceUpdateResponse> resp = restTemplate.postForEntity(
                "/api/accounts/internal/" + accountId + "/debit",
                request, BalanceUpdateResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private ResponseEntity<String> debitRaw(Long accountId, BigDecimal amount) {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setAmount(amount);
        request.setReference("test-debit");
        return restTemplate.postForEntity(
                "/api/accounts/internal/" + accountId + "/debit",
                request, String.class);
    }
}
