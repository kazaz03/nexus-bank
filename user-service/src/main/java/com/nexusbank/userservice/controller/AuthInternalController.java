package com.nexusbank.userservice.controller;

import com.nexusbank.userservice.service.TokenRevocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for inter-service calls only — not routed through the public API gateway.
 * The API gateway calls this to verify a token has not been revoked before forwarding requests.
 */
@RestController
@RequestMapping("/api/internal/auth")
class AuthInternalController {

    private final TokenRevocationService tokenRevocationService;

    AuthInternalController(TokenRevocationService tokenRevocationService) {
        this.tokenRevocationService = tokenRevocationService;
    }

    @GetMapping("/revoked/{jti}")
    public ResponseEntity<Void> isRevoked(@PathVariable String jti) {
        return tokenRevocationService.isRevoked(jti)
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                : ResponseEntity.ok().build();
    }
}
