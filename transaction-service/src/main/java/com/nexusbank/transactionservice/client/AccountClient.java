package com.nexusbank.transactionservice.client;

import com.nexusbank.transactionservice.client.dto.AccountView;
import com.nexusbank.transactionservice.client.dto.BalanceUpdateCommand;
import com.nexusbank.transactionservice.client.dto.BalanceUpdateResult;
import com.nexusbank.transactionservice.exception.AccountServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Synchronous client used by Transaction Service to communicate with
 * Account Service over HTTP.
 *
 * Uses two underlying RestTemplate beans:
 * <ul>
 *   <li>{@code loadBalancedRestTemplate} — default; resolves the logical
 *       service name {@code account-service} via Eureka and Spring Cloud
 *       LoadBalancer (round-robin across registered instances).</li>
 *   <li>{@code directRestTemplate} — fallback used only when the caller
 *       explicitly opts in via {@link #useDirectMode}, primarily for the
 *       load-balancing demo and integration tests.</li>
 * </ul>
 */
@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    /** Logical service name resolved through Eureka by the load-balanced RestTemplate. */
    private static final String LOGICAL_BASE_URL = "http://account-service";

    private final RestTemplate loadBalancedRestTemplate;
    private final RestTemplate directRestTemplate;
    private final String directBaseUrl;

    public AccountClient(
            @Qualifier("loadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
            @Qualifier("directRestTemplate") RestTemplate directRestTemplate,
            @Value("${app.account-service.direct-url:http://localhost:8082}") String directBaseUrl) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.directRestTemplate = directRestTemplate;
        this.directBaseUrl = stripTrailingSlash(directBaseUrl);
    }

    /**
     * Fetches an account by IBAN. Throws {@link AccountServiceException}
     * with retryable=false and HTTP 422 when the account does not exist.
     */
    public AccountView getByIban(String iban, boolean useDirectMode) {
        String url = baseUrl(useDirectMode) + "/api/accounts/internal/by-iban/" + iban;
        return executeGet(url, AccountView.class, "getByIban(" + iban + ")");
    }

    /** Fetches an account by primary key. */
    public AccountView getById(Long accountId, boolean useDirectMode) {
        String url = baseUrl(useDirectMode) + "/api/accounts/internal/" + accountId;
        return executeGet(url, AccountView.class, "getById(" + accountId + ")");
    }

    /**
     * Debits the given amount from the account. Returns the new balance
     * on success. A response of HTTP 422 from Account Service (e.g.
     * insufficient funds) is propagated as a non-retryable
     * {@link AccountServiceException}.
     */
    public BalanceUpdateResult debit(Long accountId, BigDecimal amount, String reference, String idempotencyKey) {
        String url = baseUrl(false) + "/api/accounts/internal/" + accountId + "/debit";
        BalanceUpdateCommand body = new BalanceUpdateCommand(amount, reference, idempotencyKey);
        return executePost(url, body, BalanceUpdateResult.class,
                "debit(" + accountId + ", " + amount + ")");
    }

    /**
     * Credits the given amount to the account. Used both for the second
     * leg of a transfer and for compensation (refund) after a failed
     * credit on the destination.
     */
    public BalanceUpdateResult credit(Long accountId, BigDecimal amount, String reference, String idempotencyKey) {
        String url = baseUrl(false) + "/api/accounts/internal/" + accountId + "/credit";
        BalanceUpdateCommand body = new BalanceUpdateCommand(amount, reference, idempotencyKey);
        return executePost(url, body, BalanceUpdateResult.class,
                "credit(" + accountId + ", " + amount + ")");
    }

    private <T> T executeGet(String url, Class<T> responseType, String operation) {
        try {
            ResponseEntity<T> response = loadBalancedRestTemplate.getForEntity(url, responseType);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex, operation);
        } catch (HttpServerErrorException ex) {
            throw mapServerError(ex, operation);
        } catch (ResourceAccessException ex) {
            // Network failure: connection refused, DNS, timeout, no Eureka instances available.
            log.warn("Account Service unreachable during {}: {}", operation, ex.getMessage());
            throw new AccountServiceException(
                    "Account Service is unreachable: " + ex.getMessage(),
                    ex, true, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private <T> T executePost(String url, Object body, Class<T> responseType, String operation) {
        try {
            ResponseEntity<T> response = loadBalancedRestTemplate.postForEntity(url, body, responseType);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex, operation);
        } catch (HttpServerErrorException ex) {
            throw mapServerError(ex, operation);
        } catch (ResourceAccessException ex) {
            log.warn("Account Service unreachable during {}: {}", operation, ex.getMessage());
            throw new AccountServiceException(
                    "Account Service is unreachable: " + ex.getMessage(),
                    ex, true, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private AccountServiceException mapClientError(HttpClientErrorException ex, String operation) {
        HttpStatusCode status = ex.getStatusCode();
        log.info("Account Service returned {} for {}: {}", status, operation, ex.getResponseBodyAsString());
        // 4xx — business rule violation; non-retryable.
        return new AccountServiceException(
                "Account Service rejected " + operation + ": " + ex.getStatusText(),
                ex, false,
                status.value() == 404
                        ? HttpStatus.UNPROCESSABLE_ENTITY
                        : HttpStatus.valueOf(status.value()));
    }

    private AccountServiceException mapServerError(HttpServerErrorException ex, String operation) {
        log.warn("Account Service returned {} for {}: {}", ex.getStatusCode(), operation,
                ex.getResponseBodyAsString());
        return new AccountServiceException(
                "Account Service failed during " + operation + ": " + ex.getStatusText(),
                ex, true, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private String baseUrl(boolean useDirectMode) {
        return useDirectMode ? directBaseUrl : LOGICAL_BASE_URL;
    }

    private static String stripTrailingSlash(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }
}
