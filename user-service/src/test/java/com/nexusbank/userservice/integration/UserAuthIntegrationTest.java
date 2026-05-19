package com.nexusbank.userservice.integration;

import com.nexusbank.userservice.dto.request.LoginRequest;
import com.nexusbank.userservice.dto.response.LoginResponse;
import com.nexusbank.userservice.model.User;
import com.nexusbank.userservice.repository.CustomerRepository;
import com.nexusbank.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for authentication: login endpoint + JWT issuance.
 *
 * Exercises the full request → Spring Security filter chain → AuthService →
 * JwtUtil → MySQL stack in a real embedded server, backed by Testcontainers.
 *
 * The DataLoader seeds four demo users at context startup (DB is empty on
 * create-drop). Tests below use those seeded users directly.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class UserAuthIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    // DataLoader seeds these users at context startup since the DB is empty.
    // Credentials are taken directly from DataLoader.java.
    private static final String TELLER_EMAIL    = "ana.kovacevic@nexusbank.com";
    private static final String TELLER_PASSWORD = "password2";
    private static final String ADMIN_EMAIL     = "admin@nexusbank.com";
    private static final String ADMIN_PASSWORD  = "admin123";
    private static final String CUSTOMER_EMAIL  = "marko.nikolic@nexusbank.com";
    private static final String CUSTOMER_PASS   = "password1";

    // ── Login — happy paths ───────────────────────────────────────────────────

    @Test
    void login_asSeededTeller_returns200WithValidJwt() {
        LoginRequest request = loginRequest(TELLER_EMAIL, TELLER_PASSWORD);

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/auth/login", request, LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getToken()).isNotBlank();
        assertThat(body.getEmail()).isEqualTo(TELLER_EMAIL);
        assertThat(body.getRole()).isEqualTo("TELLER");
        assertThat(body.getUserId()).isPositive();
        assertThat(body.getExpiresIn()).isPositive();
    }

    @Test
    void login_asSeededAdmin_returns200WithAdminRole() {
        LoginRequest request = loginRequest(ADMIN_EMAIL, ADMIN_PASSWORD);

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/auth/login", request, LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getRole()).isEqualTo("ADMIN");
    }

    @Test
    void login_asSeededCustomer_returns200WithCustomerRole() {
        LoginRequest request = loginRequest(CUSTOMER_EMAIL, CUSTOMER_PASS);

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/auth/login", request, LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getRole()).isEqualTo("CUSTOMER");
    }

    // ── Login — failure paths ─────────────────────────────────────────────────

    @Test
    void login_withWrongPassword_returns4xx() {
        LoginRequest request = loginRequest(TELLER_EMAIL, "wrongpassword");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", request, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void login_withUnknownEmail_returns4xx() {
        LoginRequest request = loginRequest("nobody@notexists.com", "irrelevant");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", request, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void login_withInactiveAccount_returns4xx() {
        // Insert an inactive user specifically for this test
        User inactive = new User();
        inactive.setEmail("inactive.user@integration.test");
        inactive.setPasswordHash(passwordEncoder.encode("Password123!"));
        inactive.setFirstName("Inactive");
        inactive.setLastName("User");
        inactive.setRole(User.Role.CUSTOMER);
        inactive.setCreatedAt(LocalDateTime.now());
        inactive.setIsActive(false);
        userRepository.save(inactive);

        LoginRequest request = loginRequest("inactive.user@integration.test", "Password123!");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", request, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void login_withBlankEmail_returns400() {
        LoginRequest request = loginRequest("", "Password123!");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_withInvalidEmailFormat_returns400() {
        LoginRequest request = loginRequest("not-an-email", "Password123!");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── JWT is usable for subsequent calls ────────────────────────────────────

    @Test
    void jwtFromLogin_isAcceptedByProtectedEndpoint() {
        // 1. Login as the seeded customer
        LoginResponse loginBody = restTemplate.postForEntity(
                "/api/auth/login",
                loginRequest(CUSTOMER_EMAIL, CUSTOMER_PASS),
                LoginResponse.class).getBody();
        assertThat(loginBody).isNotNull();
        String jwt = loginBody.getToken();

        // 2. Use the JWT to hit a protected endpoint (GET /api/customers/{id})
        //    The customer from DataLoader has customer record; resolve its ID.
        Long userId = loginBody.getUserId();
        customerRepository.findAll().stream()
                .filter(c -> c.getUser().getId().equals(userId))
                .findFirst()
                .ifPresent(customer -> {
                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    headers.setBearerAuth(jwt);
                    ResponseEntity<String> protectedResp = restTemplate.exchange(
                            "/api/customers/" + customer.getId(),
                            org.springframework.http.HttpMethod.GET,
                            new org.springframework.http.HttpEntity<>(headers),
                            String.class);
                    assertThat(protectedResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                });
    }

    // ── Logout + token revocation ─────────────────────────────────────────────

    @Test
    void logout_invalidatesToken_subsequentRequestIs4xx() {
        // 1. Login and obtain a valid JWT
        LoginResponse login = restTemplate.postForEntity(
                "/api/auth/login",
                loginRequest(TELLER_EMAIL, TELLER_PASSWORD),
                LoginResponse.class).getBody();
        assertThat(login).isNotNull();
        String jwt = login.getToken();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(jwt);

        // 2. Logout — revoke the token
        ResponseEntity<Void> logoutResp = restTemplate.exchange(
                "/api/auth/logout",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(headers),
                Void.class);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 3. The revoked token must no longer grant access to protected endpoints
        ResponseEntity<String> afterLogout = restTemplate.exchange(
                "/api/customers",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);
        assertThat(afterLogout.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void logout_withNoToken_returns204WithoutError() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/auth/logout", null, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void logout_calledTwiceWithSameToken_returns204BothTimes() {
        LoginResponse login = restTemplate.postForEntity(
                "/api/auth/login",
                loginRequest(ADMIN_EMAIL, ADMIN_PASSWORD),
                LoginResponse.class).getBody();
        assertThat(login).isNotNull();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(login.getToken());
        var entity = new org.springframework.http.HttpEntity<Void>(headers);

        assertThat(restTemplate.exchange("/api/auth/logout",
                org.springframework.http.HttpMethod.POST, entity, Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(restTemplate.exchange("/api/auth/logout",
                org.springframework.http.HttpMethod.POST, entity, Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }
}
