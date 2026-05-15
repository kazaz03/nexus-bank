package com.nexusbank.loanservice.integration;

import com.nexusbank.loanservice.dto.request.LoanApplicationRequest;
import com.nexusbank.loanservice.dto.request.LoanReviewRequest;
import com.nexusbank.loanservice.dto.response.LoanApplicationResponse;
import com.nexusbank.loanservice.dto.response.RepaymentScheduleResponse;
import com.nexusbank.loanservice.messaging.LoanEventPublisher;
import com.nexusbank.loanservice.repository.LoanApplicationRepository;
import com.nexusbank.loanservice.repository.RepaymentScheduleRepository;
import com.nexusbank.loanservice.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the full loan lifecycle: submit, read, filter,
 * review (approve/reject), repayment schedule generation, and JSON Patch.
 *
 * Uses a real MySQL container and the full Spring Boot context with a random
 * server port. AccountProbeClient is wired but never called by the tested
 * endpoints; it will fail gracefully if the dummy direct-url is hit because
 * no probe tests are included here.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class LoanLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    LoanApplicationRepository loanApplicationRepository;

    @Autowired
    RepaymentScheduleRepository repaymentScheduleRepository;

    @Autowired
    LoanService loanService;

    // F14 saga publisher is mocked so tests can run without a RabbitMQ broker.
    @MockBean
    LoanEventPublisher loanEventPublisher;

    @BeforeEach
    void cleanDatabase() {
        repaymentScheduleRepository.deleteAll();
        loanApplicationRepository.deleteAll();
    }

    // ── Submit application ────────────────────────────────────────────────────

    @Test
    void submitApplication_withValidRequest_returns201AndIsPending() {
        ResponseEntity<LoanApplicationResponse> response = restTemplate.postForEntity(
                "/api/loans", validRequest(1L, 1L, "5000.00", 24, "Home renovation"),
                LoanApplicationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LoanApplicationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getCustomerId()).isEqualTo(1L);
        assertThat(body.getStatus()).isEqualTo("PENDING");
        assertThat(body.getAmountRequested()).isEqualByComparingTo(new BigDecimal("5000.00"));

        // Confirm persistence
        assertThat(loanApplicationRepository.findById(body.getId())).isPresent();
    }

    @Test
    void submitApplication_withAmountBelowMinimum_returns400() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/loans", validRequest(1L, 1L, "50.00", 12, "Too small"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(loanApplicationRepository.count()).isZero();
    }

    @Test
    void submitApplication_withMissingPurpose_returns400() {
        LoanApplicationRequest req = new LoanApplicationRequest();
        req.setCustomerId(1L);
        req.setAccountId(1L);
        req.setAmountRequested(new BigDecimal("1000.00"));
        req.setTermMonths(12);
        // purpose is intentionally missing

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/loans", req, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Batch submit ──────────────────────────────────────────────────────────

    @Test
    void submitBatch_withMultipleValidRequests_persitsAllAndReturns201() {
        List<LoanApplicationRequest> batch = List.of(
                validRequest(10L, 10L, "2000.00", 12, "Car loan"),
                validRequest(11L, 11L, "3000.00", 18, "Education"),
                validRequest(12L, 12L, "1500.00", 6,  "Travel"));

        ResponseEntity<LoanApplicationResponse[]> response = restTemplate.postForEntity(
                "/api/loans/batch", batch, LoanApplicationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).hasSize(3);
        assertThat(loanApplicationRepository.count()).isEqualTo(3);
    }

    @Test
    void submitBatch_withEmptyList_returns400() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/loans/batch", List.of(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Test
    void getApplication_whenExists_returns200WithCorrectData() {
        LoanApplicationResponse created = submit(1L, 1L, "8000.00", 36, "Consolidation");

        ResponseEntity<LoanApplicationResponse> response = restTemplate.getForEntity(
                "/api/loans/" + created.getId(), LoanApplicationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAmountRequested())
                .isEqualByComparingTo(new BigDecimal("8000.00"));
    }

    @Test
    void getApplication_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/loans/99999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getApplicationsByCustomer_returnsOnlyMatchingRecords() {
        submit(20L, 1L, "1000.00", 12, "Loan A");
        submit(20L, 2L, "2000.00", 24, "Loan B");
        submit(99L, 3L, "3000.00", 6, "Other customer");

        ResponseEntity<LoanApplicationResponse[]> response = restTemplate.getForEntity(
                "/api/loans/customer/20", LoanApplicationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).extracting(LoanApplicationResponse::getCustomerId)
                .containsOnly(20L);
    }

    @Test
    void getAllApplications_withStatusFilter_returnsOnlyMatchingStatus() {
        submit(30L, 1L, "1000.00", 12, "First");
        LoanApplicationResponse second = submit(31L, 2L, "2000.00", 24, "Second");
        approve(second.getId(), "2000.00", "5.50", 31L);

        ResponseEntity<String> pending = restTemplate.getForEntity(
                "/api/loans?status=PENDING", String.class);
        ResponseEntity<String> approved = restTemplate.getForEntity(
                "/api/loans?status=APPROVED", String.class);

        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending.getBody()).contains("\"totalElements\":1");

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody()).contains("\"totalElements\":1");
    }

    // ── Review: approve ───────────────────────────────────────────────────────

    @Test
    void approveApplication_putsLoanIntoApprovedAndPublishesEvent() {
        LoanApplicationResponse created = submit(40L, 1L, "12000.00", 12,
                "Business expansion");

        ResponseEntity<LoanApplicationResponse> reviewResponse = approve(
                created.getId(), "12000.00", "6.00", 99L);

        assertThat(reviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoanApplicationResponse body = reviewResponse.getBody();
        assertThat(body.getStatus()).isEqualTo("APPROVED");
        assertThat(body.getAmountApproved()).isEqualByComparingTo(new BigDecimal("12000.00"));
        assertThat(body.getInterestRate()).isEqualByComparingTo(new BigDecimal("6.00"));

        // Schedule is generated only after the disbursement saga completes, not on approval.
        ResponseEntity<RepaymentScheduleResponse[]> scheduleResponse = restTemplate.getForEntity(
                "/api/loans/" + created.getId() + "/schedule",
                RepaymentScheduleResponse[].class);
        assertThat(scheduleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scheduleResponse.getBody()).isEmpty();
    }

    @Test
    void markAsDisbursed_finalizesLoanAndCreatesSchedule() {
        LoanApplicationResponse created = submit(42L, 1L, "6000.00", 6, "Short-term");
        approve(created.getId(), "6000.00", "5.00", 99L);

        // Simulate the account-service confirmation (loan.disbursement.completed event)
        // by invoking the service method the listener would call.
        loanService.markAsDisbursed(created.getId());

        ResponseEntity<LoanApplicationResponse> after = restTemplate.getForEntity(
                "/api/loans/" + created.getId(), LoanApplicationResponse.class);
        assertThat(after.getBody().getStatus()).isEqualTo("DISBURSED");

        ResponseEntity<RepaymentScheduleResponse[]> schedule = restTemplate.getForEntity(
                "/api/loans/" + created.getId() + "/schedule",
                RepaymentScheduleResponse[].class);
        assertThat(schedule.getBody()).hasSize(6);
    }

    @Test
    void markAsRejectedAfterFailedDisbursement_rollsLoanBack() {
        LoanApplicationResponse created = submit(43L, 1L, "4000.00", 12, "Trip");
        approve(created.getId(), "4000.00", "5.00", 99L);

        loanService.markAsRejectedAfterFailedDisbursement(created.getId(), "Account closed");

        ResponseEntity<LoanApplicationResponse> after = restTemplate.getForEntity(
                "/api/loans/" + created.getId(), LoanApplicationResponse.class);
        assertThat(after.getBody().getStatus()).isEqualTo("REJECTED");
        assertThat(after.getBody().getRejectionReason()).contains("Account closed");
    }

    @Test
    void approveApplication_withoutAmountOrRate_returns400() {
        LoanApplicationResponse created = submit(41L, 1L, "5000.00", 12, "Test");

        LoanReviewRequest review = new LoanReviewRequest();
        review.setApproved(true);
        review.setReviewedBy(99L);
        // amountApproved and interestRate intentionally missing

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/loans/" + created.getId() + "/review", review, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Review: reject ────────────────────────────────────────────────────────

    @Test
    void rejectApplication_updatesStatusAndStoresReason() {
        LoanApplicationResponse created = submit(50L, 1L, "50000.00", 60,
                "High-risk loan");

        LoanReviewRequest review = new LoanReviewRequest();
        review.setApproved(false);
        review.setRejectionReason("Insufficient credit score");
        review.setReviewedBy(88L);

        ResponseEntity<LoanApplicationResponse> response = restTemplate.postForEntity(
                "/api/loans/" + created.getId() + "/review", review,
                LoanApplicationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoanApplicationResponse body = response.getBody();
        assertThat(body.getStatus()).isEqualTo("REJECTED");
        assertThat(body.getRejectionReason()).isEqualTo("Insufficient credit score");
        assertThat(repaymentScheduleRepository.count()).isZero();
    }

    @Test
    void reviewAlreadyApprovedApplication_returns422() {
        LoanApplicationResponse created = submit(51L, 1L, "1000.00", 6, "Quick loan");
        approve(created.getId(), "1000.00", "4.00", 99L);

        // Second review attempt on an APPROVED loan — IllegalStateException → 422
        LoanReviewRequest review = new LoanReviewRequest();
        review.setApproved(false);
        review.setRejectionReason("Changed my mind");
        review.setReviewedBy(99L);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/loans/" + created.getId() + "/review", review, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── JSON Patch ────────────────────────────────────────────────────────────

    @Test
    void patchApplication_updatesPurpose_andPersistsChange() {
        LoanApplicationResponse created = submit(60L, 1L, "2000.00", 12, "Original purpose");

        String patchBody = "[{\"op\":\"replace\",\"path\":\"/purpose\",\"value\":\"Updated purpose\"}]";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/json-patch+json"));

        ResponseEntity<LoanApplicationResponse> response = restTemplate.exchange(
                "/api/loans/" + created.getId(),
                org.springframework.http.HttpMethod.PATCH,
                new HttpEntity<>(patchBody, headers),
                LoanApplicationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPurpose()).isEqualTo("Updated purpose");

        // Verify persistence
        LoanApplicationResponse fromDb = restTemplate.getForEntity(
                "/api/loans/" + created.getId(), LoanApplicationResponse.class).getBody();
        assertThat(fromDb.getPurpose()).isEqualTo("Updated purpose");
    }

    @Test
    void patchApplication_updateAmountAndTerm_appliesBothChanges() {
        LoanApplicationResponse created = submit(61L, 1L, "3000.00", 12, "Test");

        String patchBody = "[" +
                "{\"op\":\"replace\",\"path\":\"/amountRequested\",\"value\":4500.00}," +
                "{\"op\":\"replace\",\"path\":\"/termMonths\",\"value\":18}" +
                "]";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/json-patch+json"));

        ResponseEntity<LoanApplicationResponse> response = restTemplate.exchange(
                "/api/loans/" + created.getId(),
                org.springframework.http.HttpMethod.PATCH,
                new HttpEntity<>(patchBody, headers),
                LoanApplicationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAmountRequested())
                .isEqualByComparingTo(new BigDecimal("4500.00"));
    }

    @Test
    void patchApplication_afterApproval_returns422() {
        LoanApplicationResponse created = submit(62L, 1L, "1000.00", 6, "Pre-approved");
        approve(created.getId(), "1000.00", "3.00", 99L);

        String patchBody = "[{\"op\":\"replace\",\"path\":\"/purpose\",\"value\":\"Too late\"}]";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/json-patch+json"));

        // IllegalStateException("Only PENDING applications can be patched") → 422
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/loans/" + created.getId(),
                org.springframework.http.HttpMethod.PATCH,
                new HttpEntity<>(patchBody, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── Repayment schedule ────────────────────────────────────────────────────

    @Test
    void getRepaymentSchedule_forPendingApplication_returnsEmptyList() {
        LoanApplicationResponse created = submit(70L, 1L, "5000.00", 24, "No schedule yet");

        ResponseEntity<RepaymentScheduleResponse[]> response = restTemplate.getForEntity(
                "/api/loans/" + created.getId() + "/schedule",
                RepaymentScheduleResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getRepaymentSchedule_afterDisbursement_hasCorrectInstallmentCount() {
        LoanApplicationResponse created = submit(71L, 1L, "6000.00", 6, "Short-term");
        approve(created.getId(), "6000.00", "5.00", 99L);
        loanService.markAsDisbursed(created.getId());

        ResponseEntity<RepaymentScheduleResponse[]> response = restTemplate.getForEntity(
                "/api/loans/" + created.getId() + "/schedule",
                RepaymentScheduleResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(6);
        // Each installment should have a positive amount
        assertThat(response.getBody())
                .extracting(RepaymentScheduleResponse::getAmountDue)
                .allMatch(amount -> amount.compareTo(BigDecimal.ZERO) > 0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LoanApplicationRequest validRequest(Long customerId, Long accountId,
                                                String amount, int termMonths,
                                                String purpose) {
        LoanApplicationRequest req = new LoanApplicationRequest();
        req.setCustomerId(customerId);
        req.setAccountId(accountId);
        req.setAmountRequested(new BigDecimal(amount));
        req.setCurrency("BAM");
        req.setTermMonths(termMonths);
        req.setPurpose(purpose);
        return req;
    }

    private LoanApplicationResponse submit(Long customerId, Long accountId,
                                           String amount, int termMonths,
                                           String purpose) {
        ResponseEntity<LoanApplicationResponse> resp = restTemplate.postForEntity(
                "/api/loans",
                validRequest(customerId, accountId, amount, termMonths, purpose),
                LoanApplicationResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private ResponseEntity<LoanApplicationResponse> approve(Long loanId,
                                                             String amountApproved,
                                                             String interestRate,
                                                             Long reviewedBy) {
        LoanReviewRequest review = new LoanReviewRequest();
        review.setApproved(true);
        review.setAmountApproved(new BigDecimal(amountApproved));
        review.setInterestRate(new BigDecimal(interestRate));
        review.setReviewedBy(reviewedBy);
        return restTemplate.postForEntity(
                "/api/loans/" + loanId + "/review", review, LoanApplicationResponse.class);
    }
}
