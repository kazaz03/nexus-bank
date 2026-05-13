package com.nexusbank.apigateway.filter;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * Tests for JwtAuthenticationFilter.
 *
 * Uses WebTestClient against the running gateway context.
 * All downstream routes point to lb:// URIs which won't resolve without
 * Eureka, so we only test that the filter accepts/rejects at the gateway
 * level — we don't need downstream services to be running.
 *
 * Public paths return anything other than 401.
 * Protected paths without a token return 401.
 * Protected paths with an invalid token return 401.
 * Protected paths with a valid token are forwarded (may get 503 because
 * downstream is not running, but NOT 401).
 *
 * Tokens are signed here with the RSA private key (from test resources).
 * The gateway verifies them using only the public key — mirroring the
 * production separation of signing and verification responsibilities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JwtAuthenticationFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    private String validToken;
    private String expiredToken;

    @BeforeEach
    void setUp() throws Exception {
        RSAPrivateKey privateKey = loadTestPrivateKey();

        validToken = Jwts.builder()
                .subject("admin@nexusbank.com")
                .claim("role", "ADMIN")
                .claim("userId", 1L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86_400_000))
                .signWith(privateKey)
                .compact();

        expiredToken = Jwts.builder()
                .subject("admin@nexusbank.com")
                .claim("role", "ADMIN")
                .claim("userId", 1L)
                .issuedAt(new Date(System.currentTimeMillis() - 200_000))
                .expiration(new Date(System.currentTimeMillis() - 100_000))
                .signWith(privateKey)
                .compact();
    }

    // ── public paths ─────────────────────────────────────────────────────────

    @Test
    void loginPath_withNoToken_isNotRejectedByFilter() {
        webTestClient.post()
                .uri("/api/auth/login")
                .exchange()
                .expectStatus().value(status -> {
                    assert status != 401 : "Public path should not return 401";
                });
    }

    @Test
    void registerPath_withNoToken_isNotRejectedByFilter() {
        webTestClient.post()
                .uri("/api/auth/register")
                .exchange()
                .expectStatus().value(status -> {
                    assert status != 401 : "Public path should not return 401";
                });
    }

    // ── protected paths — no token ────────────────────────────────────────────

    @Test
    void protectedPath_withNoToken_returns401() {
        webTestClient.get()
                .uri("/api/customers")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedPath_withMalformedHeader_returns401() {
        webTestClient.get()
                .uri("/api/accounts/1")
                .header("Authorization", "NotBearer something")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── protected paths — bad token ───────────────────────────────────────────

    @Test
    void protectedPath_withExpiredToken_returns401() {
        webTestClient.get()
                .uri("/api/customers")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedPath_withGarbageToken_returns401() {
        webTestClient.get()
                .uri("/api/loans")
                .header("Authorization", "Bearer this.is.not.a.jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── protected paths — valid token ─────────────────────────────────────────

    @Test
    void protectedPath_withValidToken_isForwardedByFilter() {
        webTestClient.get()
                .uri("/api/customers")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().value(status -> {
                    assert status != 401 : "Valid token should not be rejected by filter";
                });
    }

    @Test
    void protectedPath_withValidToken_errorBodyIsJson() {
        webTestClient.get()
                .uri("/api/customers")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").exists()
                .jsonPath("$.path").isEqualTo("/api/customers");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static RSAPrivateKey loadTestPrivateKey() throws Exception {
        byte[] pemBytes = new ClassPathResource("keys/private.pem").getInputStream().readAllBytes();
        String pem = new String(pemBytes, StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }
}
