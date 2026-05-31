package com.nexusbank.apigateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reactive client that asks user-service whether a given JWT ID (jti) has been revoked.
 *
 * Returns {@code true} (revoked) when user-service responds 401, {@code false} otherwise.
 * On any connectivity error the check fails open — the token is treated as valid — to
 * avoid blocking all authenticated traffic if user-service is temporarily unavailable.
 *
 * Uses a direct URL configured via USER_SERVICE_URL env var (defaults to localhost for
 * local development). In Docker the docker-compose.yml sets this to http://user-service:8081.
 */
@Component
public class TokenRevocationClient {

    private final WebClient webClient;

    public TokenRevocationClient(WebClient.Builder webClientBuilder,
                                 @Value("${USER_SERVICE_URL:http://localhost:8081}") String userServiceUrl) {
        this.webClient = webClientBuilder
                .baseUrl(userServiceUrl)
                .build();
    }

    public Mono<Boolean> isRevoked(String jti) {
        return webClient.get()
                .uri("/api/internal/auth/revoked/{jti}", jti)
                .exchangeToMono(response -> {
                    boolean revoked = response.statusCode().value() == HttpStatus.UNAUTHORIZED.value();
                    return response.releaseBody().thenReturn(revoked);
                })
                .onErrorResume(e -> Mono.just(false));
    }
}
