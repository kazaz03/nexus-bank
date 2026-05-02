package com.nexusbank.transactionservice.controller;

import com.nexusbank.transactionservice.client.AccountClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Exposes a load-balancing probe used by the measurement script in
 * {@code scripts/measure-transaction-lb.ps1}.
 *
 * The probe exercises the same {@code @LoadBalanced RestTemplate} used by
 * {@link AccountClient} during the F11 transfer flow, so the resulting
 * round-robin distribution is direct evidence that synchronous calls from
 * Transaction Service (debit/credit) are also routed through Eureka.
 */
@RestController
@RequestMapping("/api/transactions/probe")
public class TransactionProbeController {

    private static final String INSTANCE_PATH = "/api/accounts/probe/instance";

    private final RestTemplate loadBalancedRestTemplate;
    private final RestTemplate directRestTemplate;

    public TransactionProbeController(
            @Qualifier("loadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
            @Qualifier("directRestTemplate") RestTemplate directRestTemplate) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.directRestTemplate = directRestTemplate;
    }

    @GetMapping("/account-instance")
    public ResponseEntity<Map<String, Object>> probe(
            @RequestParam(defaultValue = "lb") String mode,
            @RequestParam(required = false) String directBaseUrl) {

        boolean directMode = "direct".equalsIgnoreCase(mode);
        if (!directMode && !"lb".equalsIgnoreCase(mode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "mode must be 'lb' or 'direct'"));
        }

        String target = directMode
                ? sanitize(directBaseUrl != null ? directBaseUrl : "http://localhost:8082") + INSTANCE_PATH
                : "http://account-service" + INSTANCE_PATH;

        long start = System.nanoTime();
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = (directMode ? directRestTemplate : loadBalancedRestTemplate)
                .getForEntity(target, Map.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", directMode ? "direct" : "lb");
        payload.put("target", target);
        payload.put("durationMs", elapsedMs);
        payload.put("downstream", response.getBody());
        return ResponseEntity.ok(payload);
    }

    private static String sanitize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
