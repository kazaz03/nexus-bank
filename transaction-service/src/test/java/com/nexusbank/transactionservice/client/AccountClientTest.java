package com.nexusbank.transactionservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.transactionservice.client.dto.AccountView;
import com.nexusbank.transactionservice.client.dto.BalanceUpdateResult;
import com.nexusbank.transactionservice.exception.AccountServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

/**
 * Tests for {@link AccountClient} using Spring's MockRestServiceServer to
 * intercept and stub HTTP calls without a running Account Service.
 *
 * Scenarios verified:
 *  • Happy paths — successful GET/POST round-trips with proper body parsing.
 *  • 4xx responses are mapped to non-retryable {@link AccountServiceException}.
 *  • 5xx responses are mapped to retryable {@link AccountServiceException}.
 *  • Network failures (connection refused / IO error) are mapped to retryable
 *    503 errors.
 *
 * MockRestServiceServer pattern documented at:
 * <a href="https://www.baeldung.com/spring-mock-rest-template">Baeldung</a>.
 */
class AccountClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private AccountClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Build a real RestTemplate; we'll bind a MockRestServiceServer to it
        // so HTTP calls never leave the JVM.
        restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(5))
                .build();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        objectMapper = new ObjectMapper();

        // Inject the same RestTemplate as both load-balanced and direct beans
        // — for the test we don't care about Eureka resolution, only that the
        // client builds the correct URL and parses the response.
        client = new AccountClient(restTemplate, restTemplate, "http://localhost:8082");
    }

    // ── Happy paths ──────────────────────────────────────────────────────────

    @Test
    void getByIban_success_returnsAccountView() throws Exception {
        AccountView stubbed = new AccountView(
                1L, 100L, "BA391000000000000001", "CHECKING", "BAM",
                BigDecimal.valueOf(1000), BigDecimal.ZERO, "ACTIVE");

        mockServer.expect(requestTo("http://account-service/api/accounts/internal/by-iban/BA391000000000000001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stubbed), MediaType.APPLICATION_JSON));

        AccountView result = client.getByIban("BA391000000000000001", false);

        assertThat(result).isNotNull();
        assertThat(result.getIban()).isEqualTo("BA391000000000000001");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        mockServer.verify();
    }

    @Test
    void getByIban_directMode_usesConfiguredDirectUrl() throws Exception {
        AccountView stubbed = new AccountView(
                1L, 100L, "BA391000000000000001", "CHECKING", "BAM",
                BigDecimal.valueOf(1000), BigDecimal.ZERO, "ACTIVE");

        // In direct mode the client should hit the configured base URL
        // rather than the logical service name.
        mockServer.expect(requestTo("http://localhost:8082/api/accounts/internal/by-iban/BA391000000000000001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stubbed), MediaType.APPLICATION_JSON));

        AccountView result = client.getByIban("BA391000000000000001", true);

        assertThat(result).isNotNull();
        mockServer.verify();
    }

    @Test
    void debit_success_returnsBalanceUpdateResult() throws Exception {
        BalanceUpdateResult stubbed = new BalanceUpdateResult(
                1L, "BA391000000000000001", "BAM", BigDecimal.valueOf(750));

        mockServer.expect(requestTo("http://account-service/api/accounts/internal/1/debit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stubbed), MediaType.APPLICATION_JSON));

        BalanceUpdateResult result = client.debit(1L, BigDecimal.valueOf(250), "TRX-001", "TRX-001-debit");

        assertThat(result.getNewBalance()).isEqualByComparingTo(BigDecimal.valueOf(750));
        mockServer.verify();
    }

    @Test
    void credit_success_returnsBalanceUpdateResult() throws Exception {
        BalanceUpdateResult stubbed = new BalanceUpdateResult(
                2L, "BA392000000000000002", "BAM", BigDecimal.valueOf(750));

        mockServer.expect(requestTo("http://account-service/api/accounts/internal/2/credit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stubbed), MediaType.APPLICATION_JSON));

        BalanceUpdateResult result = client.credit(2L, BigDecimal.valueOf(250), "TRX-001", "TRX-001-credit");

        assertThat(result.getAccountId()).isEqualTo(2L);
        assertThat(result.getNewBalance()).isEqualByComparingTo(BigDecimal.valueOf(750));
        mockServer.verify();
    }

    // ── Error mapping ────────────────────────────────────────────────────────

    @Test
    void getByIban_404Response_mappedToNonRetryable422() {
        mockServer.expect(requestTo("http://account-service/api/accounts/internal/by-iban/BAD-IBAN"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("Not found"));

        assertThatThrownBy(() -> client.getByIban("BAD-IBAN", false))
                .isInstanceOf(AccountServiceException.class)
                .matches(ex -> !((AccountServiceException) ex).isRetryable(),
                        "404 should NOT be retryable")
                .matches(ex -> ((AccountServiceException) ex).getSuggestedStatus() == HttpStatus.UNPROCESSABLE_ENTITY,
                        "404 from account-service should be re-mapped to 422 for the upstream caller");
    }

    @Test
    void debit_422Response_mappedToNonRetryable422() {
        mockServer.expect(requestTo("http://account-service/api/accounts/internal/1/debit"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("Insufficient funds"));

        assertThatThrownBy(() -> client.debit(1L, BigDecimal.valueOf(250), "TRX-001", "TRX-001-debit"))
                .isInstanceOf(AccountServiceException.class)
                .matches(ex -> !((AccountServiceException) ex).isRetryable(),
                        "Business rejection should NOT be retryable");
    }

    @Test
    void debit_500Response_mappedToRetryable503() {
        mockServer.expect(requestTo("http://account-service/api/accounts/internal/1/debit"))
                .andRespond(withServerError().body("Internal error"));

        assertThatThrownBy(() -> client.debit(1L, BigDecimal.valueOf(250), "TRX-001", "TRX-001-debit"))
                .isInstanceOf(AccountServiceException.class)
                .matches(ex -> ((AccountServiceException) ex).isRetryable(),
                        "5xx should be retryable")
                .matches(ex -> ((AccountServiceException) ex).getSuggestedStatus() == HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void debit_networkFailure_mappedToRetryable503() {
        mockServer.expect(requestTo("http://account-service/api/accounts/internal/1/debit"))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));

        assertThatThrownBy(() -> client.debit(1L, BigDecimal.valueOf(250), "TRX-001", "TRX-001-debit"))
                .isInstanceOf(AccountServiceException.class)
                .matches(ex -> ((AccountServiceException) ex).isRetryable(),
                        "Network errors should be retryable")
                .hasMessageContaining("unreachable");
    }
}
