package com.nexusbank.apigateway.client;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Reactive client that asks user-service whether a given JWT ID (jti) has been revoked.
 *
 * Returns {@code true} (revoked) when user-service responds 401, {@code false} otherwise.
 * On any connectivity error the check fails open — the token is treated as valid — to
 * avoid blocking all authenticated traffic if user-service is temporarily unavailable.
 */
@Component
public class TokenRevocationClient {

    private final WebClient webClient;

    public TokenRevocationClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("lb://user-service")
                .build();
    }

    public Mono<Boolean> isRevoked(String jti) {
        return webClient.get()
                .uri("/api/internal/auth/revoked/{jti}", jti)
                .retrieve()
                .toBodilessEntity()
                .map(r -> false)
                .onErrorResume(WebClientResponseException.Unauthorized.class, e -> Mono.just(true))
                .onErrorResume(e -> Mono.just(false));
    }
}
