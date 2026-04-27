package com.nexusbank.accountservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts/probe")
public class AccountProbeController {

    @Value("${app.instance-id:${spring.application.name}:${server.port}}")
    private String instanceId;

    @Value("${server.port}")
    private String serverPort;

    @GetMapping("/instance")
    public Map<String, Object> getServingInstance() {
        return Map.of(
                "service", "account-service",
                "instanceId", instanceId,
                "serverPort", serverPort,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
