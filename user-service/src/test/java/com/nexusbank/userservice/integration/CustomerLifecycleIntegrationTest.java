package com.nexusbank.userservice.integration;

import com.nexusbank.userservice.dto.request.LoginRequest;
import com.nexusbank.userservice.dto.request.RegisterCustomerRequest;
import com.nexusbank.userservice.dto.request.UpdateCustomerRequest;
import com.nexusbank.userservice.dto.response.CustomerResponse;
import com.nexusbank.userservice.dto.response.LoginResponse;
import com.nexusbank.userservice.repository.CustomerRepository;
import com.nexusbank.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the full customer lifecycle: register, read, update.
 *
 * The DataLoader seeds a TELLER user at startup. Each test obtains a fresh JWT
 * by logging in as that teller, then exercises the customer REST API through the
 * full Spring Security filter chain → service → JPA → MySQL stack.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class CustomerLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    UserRepository userRepository;

    private static final AtomicInteger counter = new AtomicInteger(0);

    // Seeded by DataLoader
    private static final String TELLER_EMAIL    = "ana.kovacevic@nexusbank.com";
    private static final String TELLER_PASSWORD = "password2";
    private static final String ADMIN_EMAIL     = "admin@nexusbank.com";
    private static final String ADMIN_PASSWORD  = "admin123";

    @BeforeEach
    void deleteTestCustomers() {
        // Remove only customers whose id_card_number starts with "TEST" to avoid
        // touching DataLoader's seeded records
        customerRepository.findAll().stream()
                .filter(c -> c.getIdCardNumber().startsWith("TEST"))
                .forEach(c -> {
                    customerRepository.delete(c);
                    userRepository.delete(c.getUser());
                });
    }

    // ── Register customer ─────────────────────────────────────────────────────

    @Test
    void registerCustomer_asAuthenticatedTeller_creates201AndPersistsToDb() {
        String jwt = loginAs(TELLER_EMAIL, TELLER_PASSWORD);
        RegisterCustomerRequest request = validRequest("test.register@nexusbank.com");

        ResponseEntity<CustomerResponse> response = postWithAuth(
                "/api/customers", request, CustomerResponse.class, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CustomerResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getEmail()).isEqualTo("test.register@nexusbank.com");
        assertThat(body.getFirstName()).isEqualTo("Test");
        assertThat(body.getLastName()).isEqualTo("User");
        assertThat(body.getKycStatus()).isEqualTo("PENDING");

        // Confirm it actually landed in the database
        assertThat(customerRepository.findById(body.getId())).isPresent();
    }

    @Test
    void registerCustomer_withDuplicateEmail_returns409() {
        String jwt = loginAs(TELLER_EMAIL, TELLER_PASSWORD);
        RegisterCustomerRequest request = validRequest("test.duplicate@nexusbank.com");

        // First registration succeeds
        ResponseEntity<CustomerResponse> first = postWithAuth(
                "/api/customers", request, CustomerResponse.class, jwt);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second with the same email must fail
        ResponseEntity<String> second = postWithAuth(
                "/api/customers", request, String.class, jwt);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void registerCustomer_withMissingMandatoryFields_returns400() {
        String jwt = loginAs(TELLER_EMAIL, TELLER_PASSWORD);
        RegisterCustomerRequest request = new RegisterCustomerRequest();
        request.setEmail("missing.fields@nexusbank.com");
        // firstName, lastName, dateOfBirth, idCardNumber intentionally missing

        ResponseEntity<String> response = postWithAuth(
                "/api/customers", request, String.class, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerCustomer_withoutJwt_returns401Or403() {
        RegisterCustomerRequest request = validRequest("test.noauth@nexusbank.com");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/customers", request, String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void registerCustomer_withCustomerRoleJwt_returns403() {
        // Customers cannot register other customers — only TELLER/ADMIN can
        String customerJwt = loginAs("marko.nikolic@nexusbank.com", "password1");
        RegisterCustomerRequest request = validRequest("test.customerrole@nexusbank.com");

        ResponseEntity<String> response = postWithAuth(
                "/api/customers", request, String.class, customerJwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Read customer ─────────────────────────────────────────────────────────

    @Test
    void getCustomer_asAuthenticatedUser_returns200() {
        String tellerJwt = loginAs(TELLER_EMAIL, TELLER_PASSWORD);
        CustomerResponse created = postWithAuth(
                "/api/customers", validRequest("test.getone@nexusbank.com"),
                CustomerResponse.class, tellerJwt).getBody();

        ResponseEntity<CustomerResponse> response = getWithAuth(
                "/api/customers/" + created.getId(), CustomerResponse.class, tellerJwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEmail()).isEqualTo("test.getone@nexusbank.com");
    }

    @Test
    void getCustomer_whenNotFound_returns404() {
        String jwt = loginAs(TELLER_EMAIL, TELLER_PASSWORD);

        ResponseEntity<String> response = getWithAuth(
                "/api/customers/99999", String.class, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAllCustomers_asTeller_returnsListIncludingCreatedCustomer() {
        String jwt = loginAs(TELLER_EMAIL, TELLER_PASSWORD);
        postWithAuth("/api/customers", validRequest("test.list1@nexusbank.com"),
                CustomerResponse.class, jwt);
        postWithAuth("/api/customers", validRequest("test.list2@nexusbank.com"),
                CustomerResponse.class, jwt);

        ResponseEntity<CustomerResponse[]> response = getWithAuth(
                "/api/customers", CustomerResponse[].class, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
    }

    // ── Update customer ───────────────────────────────────────────────────────

    @Test
    void updateCustomer_changesAddressAndPhone_persistsToDb() {
        String jwt = loginAs(TELLER_EMAIL, TELLER_PASSWORD);
        CustomerResponse created = postWithAuth(
                "/api/customers", validRequest("test.update@nexusbank.com"),
                CustomerResponse.class, jwt).getBody();

        UpdateCustomerRequest update = new UpdateCustomerRequest();
        update.setAddress("456 Updated Avenue");
        update.setPhone("+38762999888");

        ResponseEntity<CustomerResponse> response = putWithAuth(
                "/api/customers/" + created.getId(), update, CustomerResponse.class, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CustomerResponse body = response.getBody();
        assertThat(body.getAddress()).isEqualTo("456 Updated Avenue");
        assertThat(body.getPhone()).isEqualTo("+38762999888");

        // Confirm DB reflects the change
        CustomerResponse fromDb = getWithAuth(
                "/api/customers/" + created.getId(), CustomerResponse.class, jwt).getBody();
        assertThat(fromDb.getAddress()).isEqualTo("456 Updated Avenue");
    }

    @Test
    void updateCustomer_whenNotFound_returns404() {
        String jwt = loginAs(TELLER_EMAIL, TELLER_PASSWORD);
        UpdateCustomerRequest update = new UpdateCustomerRequest();
        update.setAddress("Does Not Matter");

        ResponseEntity<String> response = putWithAuth(
                "/api/customers/99999", update, String.class, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Admin endpoint access ─────────────────────────────────────────────────

    @Test
    void adminStats_asAdmin_returns200() {
        String adminJwt = loginAs(ADMIN_EMAIL, ADMIN_PASSWORD);

        ResponseEntity<String> response = getWithAuth("/api/admin/stats", String.class, adminJwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminStats_asTeller_returns403() {
        String tellerJwt = loginAs(TELLER_EMAIL, TELLER_PASSWORD);

        ResponseEntity<String> response = getWithAuth("/api/admin/stats", String.class, tellerJwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String loginAs(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        LoginResponse body = restTemplate.postForEntity(
                "/api/auth/login", req, LoginResponse.class).getBody();
        assertThat(body).isNotNull();
        return body.getToken();
    }

    private <T> ResponseEntity<T> postWithAuth(String url, Object body,
                                               Class<T> responseType, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(url,
                new HttpEntity<>(body, headers), responseType);
    }

    private <T> ResponseEntity<T> getWithAuth(String url, Class<T> responseType, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), responseType);
    }

    private <T> ResponseEntity<T> putWithAuth(String url, Object body,
                                              Class<T> responseType, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.PUT,
                new HttpEntity<>(body, headers), responseType);
    }

    private RegisterCustomerRequest validRequest(String email) {
        int n = counter.incrementAndGet();
        RegisterCustomerRequest req = new RegisterCustomerRequest();
        req.setEmail(email);
        req.setPassword("Password123!");
        req.setFirstName("Test");
        req.setLastName("User");
        req.setDateOfBirth(LocalDate.of(1990, 1, 1));
        req.setIdCardNumber("TEST" + String.format("%06d", n));
        req.setAddress("Test Street " + n);
        req.setPhone("+38761" + String.format("%06d", n));
        return req;
    }
}
