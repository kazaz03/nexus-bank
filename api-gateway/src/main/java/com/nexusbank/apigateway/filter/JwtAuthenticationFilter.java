package com.nexusbank.apigateway.filter;

import com.nexusbank.apigateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Global filter that runs on every request before routing.
 *
 * Public paths (login, register, Swagger, actuator) are whitelisted and pass
 * through without a token. All other paths require a valid Bearer JWT.
 *
 * Tokens are verified using the RSA public key only — the gateway cannot
 * forge tokens because it never holds the private key.
 *
 * On success the filter forwards three additional headers to the downstream
 * service so it knows who is making the call without re-parsing the token:
 *   X-User-Id    — numeric user ID from the token claim
 *   X-User-Email — email (JWT subject)
 *   X-User-Role  — role string, e.g. ADMIN, CUSTOMER
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * Paths that do not require authentication.
     * Prefix-matched: a listed entry matches the path if the request path
     * starts with that string.
     */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api-docs",
            "/swagger-ui",
            "/actuator"
    );

    private final RSAPublicKey publicKey;

    public JwtAuthenticationFilter(JwtProperties jwtProperties) {
        this.publicKey = loadPublicKey(jwtProperties.getPublicKeyPath());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // CORS preflight requests carry no Authorization header and must reach
        // the CORS handler so the browser sees the proper response headers.
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = parseToken(token);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id",    String.valueOf(claims.get("userId")))
                    .header("X-User-Email", claims.getSubject())
                    .header("X-User-Role",  claims.get("role", String.class))
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Claims parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (claims.getExpiration().before(new Date())) {
            throw new JwtException("Token has expired");
        }

        return claims;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":%d,\"error\":\"%s\",\"path\":\"%s\"}",
                status.value(),
                message,
                exchange.getRequest().getURI().getPath());

        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static RSAPublicKey loadPublicKey(String path) {
        try {
            String resourcePath = path.startsWith("classpath:") ? path.substring(10) : path;
            byte[] pemBytes = new ClassPathResource(resourcePath).getInputStream().readAllBytes();
            String pem = new String(pemBytes, StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load RSA public key from: " + path, e);
        }
    }
}
