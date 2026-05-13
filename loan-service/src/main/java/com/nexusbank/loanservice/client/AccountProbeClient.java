package com.nexusbank.loanservice.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class AccountProbeClient {

    private static final String ACCOUNT_INTERNAL_PATH = "/api/accounts/internal/";
    private static final String SERVING_INSTANCE_HEADER = "X-Serving-Instance";

    private final RestTemplate loadBalancedRestTemplate;
    private final RestTemplate directRestTemplate;
    private final String defaultDirectBaseUrl;

    public AccountProbeClient(
            @Qualifier("loadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
            @Qualifier("directRestTemplate") RestTemplate directRestTemplate,
            @Value("${app.account-service.direct-url}") String defaultDirectBaseUrl) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.directRestTemplate = directRestTemplate;
        this.defaultDirectBaseUrl = defaultDirectBaseUrl;
    }

    /**
     * Fetches a real account record from account-service using either the
     * load-balanced (Eureka-resolved) or direct RestTemplate.
     *
     * Calling GET /api/accounts/internal/{accountId} exercises the full
     * production code path: Spring MVC → AccountService → JPA → MySQL.
     * This gives representative latency numbers when comparing direct vs
     * load-balanced routing.
     *
     * Instance attribution is provided by the X-Serving-Instance response
     * header that account-service stamps on every /internal/{id} response.
     */
    public Map<String, Object> probe(String mode, String directBaseUrlOverride, Long accountId) {
        boolean directMode = "direct".equalsIgnoreCase(mode);
        if (!directMode && !"lb".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unsupported mode. Use 'lb' or 'direct'.");
        }

        String baseUrl = directMode
                ? sanitizeBaseUrl(directBaseUrlOverride != null ? directBaseUrlOverride : defaultDirectBaseUrl)
                : "http://account-service";

        String url = baseUrl + ACCOUNT_INTERNAL_PATH + accountId;
        RestTemplate template = directMode ? directRestTemplate : loadBalancedRestTemplate;

        long startedAt = System.nanoTime();
        ResponseEntity<Map> response = template.exchange(url, HttpMethod.GET, null, Map.class);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        String servingInstance = response.getHeaders().getFirst(SERVING_INSTANCE_HEADER);

        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", directMode ? "direct" : "lb");
        payload.put("target", url);
        payload.put("durationMs", elapsedMs);
        payload.put("instanceId", servingInstance != null ? servingInstance : "unknown");
        payload.put("account", response.getBody());
        return payload;
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Direct base URL must not be blank");
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
