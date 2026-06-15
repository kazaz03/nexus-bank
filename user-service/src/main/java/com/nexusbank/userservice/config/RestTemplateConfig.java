package com.nexusbank.userservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Load-balanced RestTemplate used by the AdminService to aggregate
 * statistics from account/transaction/loan services for the admin
 * dashboard (F17). Logical service names are resolved through Eureka.
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
}
