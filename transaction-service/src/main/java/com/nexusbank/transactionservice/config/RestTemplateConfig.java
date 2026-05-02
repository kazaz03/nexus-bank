package com.nexusbank.transactionservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Two RestTemplate beans used for inter-service synchronous
 * communication originating from this microservice.
 *
 * <ul>
 *   <li><b>loadBalancedRestTemplate</b> — annotated with {@link LoadBalanced}
 *       so that requests targeting a logical service name (e.g.
 *       {@code http://account-service/...}) are resolved through Spring
 *       Cloud LoadBalancer using instances registered in Eureka.
 *   <li><b>directRestTemplate</b> — a plain RestTemplate intended as a
 *       fallback for diagnostics, integration tests, or environments where
 *       Eureka is intentionally bypassed (load balancing demo with a
 *       hard-coded URL).</li>
 * </ul>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    public RestTemplate directRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
