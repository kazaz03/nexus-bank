package com.nexusbank.userservice.client;

import com.nexusbank.userservice.client.dto.AccountStatsView;
import com.nexusbank.userservice.client.dto.LoanStatsView;
import com.nexusbank.userservice.client.dto.TransactionStatsView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches aggregate statistics from the account, transaction and loan
 * services for the admin dashboard (F17).
 *
 * Each call degrades gracefully: if a downstream service is unavailable the
 * client returns {@code null} for that slice rather than failing the whole
 * dashboard, so the admin still sees every metric that is reachable.
 */
@Component
public class StatsClient {

    private static final Logger log = LoggerFactory.getLogger(StatsClient.class);

    private final RestTemplate loadBalancedRestTemplate;

    public StatsClient(@Qualifier("loadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
    }

    public AccountStatsView fetchAccountStats() {
        return fetch("http://account-service/api/accounts/internal/stats",
                AccountStatsView.class, "account");
    }

    public TransactionStatsView fetchTransactionStats() {
        return fetch("http://transaction-service/api/transactions/internal/stats",
                TransactionStatsView.class, "transaction");
    }

    public LoanStatsView fetchLoanStats() {
        return fetch("http://loan-service/api/loans/internal/stats",
                LoanStatsView.class, "loan");
    }

    private <T> T fetch(String url, Class<T> type, String label) {
        try {
            return loadBalancedRestTemplate.getForObject(url, type);
        } catch (RestClientException ex) {
            log.warn("Failed to fetch {} stats from {}: {}", label, url, ex.getMessage());
            return null;
        }
    }
}
