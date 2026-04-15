package com.nexusbank.loanservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.loanservice.dto.request.LoanApplicationRequest;
import com.nexusbank.loanservice.dto.request.LoanReviewRequest;
import com.nexusbank.loanservice.dto.response.LoanApplicationResponse;
import com.nexusbank.loanservice.dto.response.RepaymentScheduleResponse;
import com.nexusbank.loanservice.exception.GlobalExceptionHandler;
import com.nexusbank.loanservice.exception.ResourceNotFoundException;
import com.nexusbank.loanservice.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoanController.class)
@Import(GlobalExceptionHandler.class)
class LoanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoanService loanService;

    @Autowired
    private ObjectMapper objectMapper;

    private LoanApplicationResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new LoanApplicationResponse();
        sampleResponse.setId(1L);
        sampleResponse.setCustomerId(10L);
        sampleResponse.setAccountId(20L);
        sampleResponse.setAmountRequested(BigDecimal.valueOf(5000));
        sampleResponse.setCurrency("BAM");
        sampleResponse.setTermMonths(12);
        sampleResponse.setPurpose("Home renovation");
        sampleResponse.setStatus("PENDING");
        sampleResponse.setCreatedAt(LocalDateTime.now());
    }

    // ── POST /api/loans ───────────────────────────────────────────────────────

    @Test
    void submitApplication_withValidRequest_returns201() throws Exception {
        LoanApplicationRequest request = new LoanApplicationRequest();
        request.setCustomerId(10L);
        request.setAccountId(20L);
        request.setAmountRequested(BigDecimal.valueOf(5000));
        request.setCurrency("BAM");
        request.setTermMonths(12);
        request.setPurpose("Home renovation");

        when(loanService.submitApplication(any(LoanApplicationRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.purpose").value("Home renovation"));
    }

    @Test
    void submitApplication_withMissingCustomerId_returns400WithFieldErrors() throws Exception {
        // customerId is @NotNull — omit it
        String body = objectMapper.writeValueAsString(
                buildRequest(null, 20L, BigDecimal.valueOf(5000), 12, "Purpose"));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists())
                .andExpect(jsonPath("$.fieldErrors.customerId").exists());
    }

    @Test
    void submitApplication_withAmountBelowMinimum_returns400() throws Exception {
        // amountRequested min is 100.00
        String body = objectMapper.writeValueAsString(
                buildRequest(10L, 20L, BigDecimal.valueOf(50), 12, "Purpose"));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amountRequested").exists());
    }

    @Test
    void submitApplication_withBlankPurpose_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                buildRequest(10L, 20L, BigDecimal.valueOf(5000), 12, ""));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.purpose").exists());
    }

    // ── GET /api/loans ────────────────────────────────────────────────────────

    @Test
    void getAllApplications_returns200WithList() throws Exception {
        when(loanService.getAllApplications()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ── GET /api/loans/{id} ───────────────────────────────────────────────────

    @Test
    void getApplication_whenFound_returns200() throws Exception {
        when(loanService.getApplication(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/loans/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.amountRequested").value(5000));
    }

    @Test
    void getApplication_whenNotFound_returns404() throws Exception {
        when(loanService.getApplication(99L))
                .thenThrow(new ResourceNotFoundException("Loan application not found: 99"));

        mockMvc.perform(get("/api/loans/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Loan application not found: 99"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ── GET /api/loans/customer/{customerId} ─────────────────────────────────

    @Test
    void getApplicationsByCustomer_returns200WithList() throws Exception {
        when(loanService.getApplicationsByCustomer(10L)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/loans/customer/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(10));
    }

    // ── POST /api/loans/{id}/review ───────────────────────────────────────────

    @Test
    void reviewApplication_approvalWithValidRequest_returns200() throws Exception {
        LoanReviewRequest request = new LoanReviewRequest();
        request.setApproved(true);
        request.setAmountApproved(BigDecimal.valueOf(5000));
        request.setInterestRate(BigDecimal.valueOf(6.5));
        request.setReviewedBy(5L);

        LoanApplicationResponse approved = new LoanApplicationResponse();
        approved.setId(1L);
        approved.setStatus("APPROVED");

        when(loanService.reviewApplication(eq(1L), any(LoanReviewRequest.class))).thenReturn(approved);

        mockMvc.perform(post("/api/loans/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void reviewApplication_whenAlreadyReviewed_returns422() throws Exception {
        LoanReviewRequest request = new LoanReviewRequest();
        request.setApproved(true);
        request.setReviewedBy(5L);

        when(loanService.reviewApplication(eq(1L), any()))
                .thenThrow(new IllegalStateException("Only PENDING applications can be reviewed"));

        mockMvc.perform(post("/api/loans/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void reviewApplication_withMissingApproved_returns400() throws Exception {
        // approved is @NotNull
        String body = "{\"reviewedBy\":5}";

        mockMvc.perform(post("/api/loans/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.approved").exists());
    }

    // ── GET /api/loans/{id}/schedule ─────────────────────────────────────────

    @Test
    void getRepaymentSchedule_whenLoanExists_returns200() throws Exception {
        RepaymentScheduleResponse schedule = new RepaymentScheduleResponse();
        schedule.setId(1L);
        schedule.setLoanApplicationId(1L);
        schedule.setInstallmentNumber(1);
        schedule.setDueDate(LocalDate.now().plusMonths(1));
        schedule.setAmountDue(BigDecimal.valueOf(450));
        schedule.setStatus("PENDING");

        when(loanService.getRepaymentSchedule(1L)).thenReturn(List.of(schedule));

        mockMvc.perform(get("/api/loans/1/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].installmentNumber").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getRepaymentSchedule_whenLoanNotFound_returns404() throws Exception {
        when(loanService.getRepaymentSchedule(99L))
                .thenThrow(new ResourceNotFoundException("Loan application not found: 99"));

        mockMvc.perform(get("/api/loans/99/schedule"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private LoanApplicationRequest buildRequest(Long customerId, Long accountId,
                                                BigDecimal amount, Integer term, String purpose) {
        LoanApplicationRequest req = new LoanApplicationRequest();
        req.setCustomerId(customerId);
        req.setAccountId(accountId);
        req.setAmountRequested(amount);
        req.setTermMonths(term);
        req.setPurpose(purpose);
        return req;
    }
}
