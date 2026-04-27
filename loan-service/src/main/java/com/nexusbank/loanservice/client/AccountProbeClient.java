package com.nexusbank.loanservice.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class AccountProbeClient {

    private static final String ACCOUNT_INSTANCE_PATH = "/api/accounts/probe/instance";

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

    public Map<String, Object> probe(String mode, String directBaseUrlOverride) {
        boolean directMode = "direct".equalsIgnoreCase(mode);
        if (!directMode && !"lb".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unsupported mode. Use 'lb' or 'direct'.");
        }

        String baseUrl = directMode
                ? sanitizeBaseUrl(directBaseUrlOverride != null ? directBaseUrlOverride : defaultDirectBaseUrl)
                : "http://account-service";

        long startedAt = System.nanoTime();
        ResponseEntity<Map> response = (directMode ? directRestTemplate : loadBalancedRestTemplate)
                .getForEntity(baseUrl + ACCOUNT_INSTANCE_PATH, Map.class);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", directMode ? "direct" : "lb");
        payload.put("target", baseUrl + ACCOUNT_INSTANCE_PATH);
        payload.put("durationMs", elapsedMs);
        payload.put("downstream", response.getBody());
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
