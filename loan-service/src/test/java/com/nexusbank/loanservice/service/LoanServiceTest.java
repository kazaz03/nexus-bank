package com.nexusbank.loanservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.nexusbank.loanservice.client.AccountProbeClient;
import com.nexusbank.loanservice.dto.request.LoanApplicationRequest;
import com.nexusbank.loanservice.dto.request.LoanReviewRequest;
import com.nexusbank.loanservice.dto.request.LoanApplicationPatchRequest;
import com.nexusbank.loanservice.dto.response.LoanApplicationResponse;
import com.nexusbank.loanservice.dto.response.RepaymentScheduleResponse;
import com.nexusbank.loanservice.exception.ResourceNotFoundException;
import com.nexusbank.loanservice.model.LoanApplication;
import com.nexusbank.loanservice.model.RepaymentSchedule;
import com.nexusbank.loanservice.repository.LoanApplicationRepository;
import com.nexusbank.loanservice.repository.RepaymentScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Mock
    private RepaymentScheduleRepository repaymentScheduleRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AccountProbeClient accountProbeClient;

    @InjectMocks
    private LoanService loanService;

    private LoanApplication pendingLoan;
    private LoanApplicationResponse loanResponse;

    @BeforeEach
    void setUp() {
        pendingLoan = new LoanApplication();
        pendingLoan.setId(1L);
        pendingLoan.setCustomerId(10L);
        pendingLoan.setAccountId(20L);
        pendingLoan.setAmountRequested(BigDecimal.valueOf(5000));
        pendingLoan.setCurrency("BAM");
        pendingLoan.setTermMonths(12);
        pendingLoan.setPurpose("Home renovation");
        pendingLoan.setStatus(LoanApplication.LoanStatus.PENDING);
        pendingLoan.setCreatedAt(LocalDateTime.now());

        loanResponse = new LoanApplicationResponse();
        loanResponse.setId(1L);
        loanResponse.setCustomerId(10L);
        loanResponse.setStatus("PENDING");
        loanResponse.setAmountRequested(BigDecimal.valueOf(5000));
    }

    // ── submitApplication ────────────────────────────────────────────────────

    @Test
    void submitApplication_savesLoanWithPendingStatus() {
        LoanApplicationRequest request = new LoanApplicationRequest();
        request.setCustomerId(10L);
        request.setAccountId(20L);
        request.setAmountRequested(BigDecimal.valueOf(5000));
        request.setCurrency("BAM");
        request.setTermMonths(12);
        request.setPurpose("Home renovation");

        when(loanApplicationRepository.save(any(LoanApplication.class))).thenReturn(pendingLoan);
        when(modelMapper.map(any(LoanApplication.class), eq(LoanApplicationResponse.class))).thenReturn(loanResponse);

        LoanApplicationResponse result = loanService.submitApplication(request);

        assertThat(result).isNotNull();
        verify(loanApplicationRepository).save(argThat(l ->
                l.getStatus() == LoanApplication.LoanStatus.PENDING));
    }

    @Test
    void submitApplicationsBatch_savesAllApplications() {
        LoanApplicationRequest requestOne = new LoanApplicationRequest();
        requestOne.setCustomerId(10L);
        requestOne.setAccountId(20L);
        requestOne.setAmountRequested(BigDecimal.valueOf(5000));
        requestOne.setCurrency("BAM");
        requestOne.setTermMonths(12);
        requestOne.setPurpose("Home renovation");

        LoanApplicationRequest requestTwo = new LoanApplicationRequest();
        requestTwo.setCustomerId(11L);
        requestTwo.setAccountId(21L);
        requestTwo.setAmountRequested(BigDecimal.valueOf(3000));
        requestTwo.setCurrency("BAM");
        requestTwo.setTermMonths(6);
        requestTwo.setPurpose("Car service");

        when(loanApplicationRepository.saveAll(any())).thenReturn(List.of(pendingLoan));
        when(modelMapper.map(any(LoanApplication.class), eq(LoanApplicationResponse.class))).thenReturn(loanResponse);

        List<LoanApplicationResponse> result = loanService.submitApplicationsBatch(List.of(requestOne, requestTwo));

        assertThat(result).hasSize(2);
        verify(loanApplicationRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }

    @Test
    void submitApplicationsBatch_withEmptyInput_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> loanService.submitApplicationsBatch(List.of()));
        verify(loanApplicationRepository, never()).saveAll(any());
    }

    // ── getApplication ────────────────────────────────────────────────────────

    @Test
    void getApplication_whenFound_returnsResponse() {
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(pendingLoan));
        when(modelMapper.map(pendingLoan, LoanApplicationResponse.class)).thenReturn(loanResponse);

        LoanApplicationResponse result = loanService.getApplication(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getApplication_whenNotFound_throwsResourceNotFoundException() {
        when(loanApplicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> loanService.getApplication(99L));
    }

    // ── getAllApplications ────────────────────────────────────────────────────

    @Test
    void getAllApplications_returnsList() {
        Page<LoanApplication> page = new PageImpl<>(List.of(pendingLoan));
        when(loanApplicationRepository.searchApplications(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);
        when(modelMapper.map(pendingLoan, LoanApplicationResponse.class)).thenReturn(loanResponse);

        Page<LoanApplicationResponse> result = loanService.getAllApplications(
                0,
                20,
                "createdAt",
                "desc",
                null,
                null,
                null,
                null);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getAllApplications_withInvalidPageSize_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> loanService.getAllApplications(0, 0, "createdAt", "desc", null, null, null, null));
    }

    @Test
    void getAllApplications_withInvalidStatus_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> loanService.getAllApplications(0, 20, "createdAt", "desc", null, "UNKNOWN", null, null));
    }

    // ── getApplicationsByCustomer ─────────────────────────────────────────────

    @Test
    void getApplicationsByCustomer_returnsFilteredList() {
        when(loanApplicationRepository.findByCustomerId(10L)).thenReturn(List.of(pendingLoan));
        when(modelMapper.map(pendingLoan, LoanApplicationResponse.class)).thenReturn(loanResponse);

        List<LoanApplicationResponse> result = loanService.getApplicationsByCustomer(10L);

        assertThat(result).hasSize(1);
    }

    @Test
    void patchApplication_whenPendingAndPatchValid_updatesFields() throws Exception {
        JsonPatch patch = mock(JsonPatch.class);
        JsonNode currentNode = mock(JsonNode.class);
        JsonNode patchedNode = mock(JsonNode.class);

        LoanApplicationPatchRequest patchedRequest = new LoanApplicationPatchRequest();
        patchedRequest.setAmountRequested(BigDecimal.valueOf(9000));
        patchedRequest.setCurrency("eur");
        patchedRequest.setTermMonths(24);
        patchedRequest.setPurpose("Education");

        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(pendingLoan));
        when(objectMapper.convertValue(any(LoanApplicationPatchRequest.class), eq(JsonNode.class))).thenReturn(currentNode);
        when(patch.apply(currentNode)).thenReturn(patchedNode);
        when(objectMapper.treeToValue(patchedNode, LoanApplicationPatchRequest.class)).thenReturn(patchedRequest);
        when(loanApplicationRepository.save(any())).thenReturn(pendingLoan);
        when(modelMapper.map(any(LoanApplication.class), eq(LoanApplicationResponse.class))).thenReturn(loanResponse);

        LoanApplicationResponse result = loanService.patchApplication(1L, patch);

        assertThat(result).isNotNull();
        verify(loanApplicationRepository).save(argThat(loan ->
                loan.getAmountRequested().compareTo(BigDecimal.valueOf(9000)) == 0
                        && loan.getTermMonths() == 24
                        && "EUR".equals(loan.getCurrency())
                        && "Education".equals(loan.getPurpose())));
    }

    @Test
    void patchApplication_whenStatusNotPending_throwsIllegalStateException() {
        JsonPatch patch = mock(JsonPatch.class);
        pendingLoan.setStatus(LoanApplication.LoanStatus.APPROVED);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(pendingLoan));

        assertThrows(IllegalStateException.class, () -> loanService.patchApplication(1L, patch));
    }

    @Test
    void patchApplication_whenPatchInvalid_throwsIllegalArgumentException() throws Exception {
        JsonPatch patch = mock(JsonPatch.class);
        JsonNode currentNode = mock(JsonNode.class);

        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(pendingLoan));
        when(objectMapper.convertValue(any(LoanApplicationPatchRequest.class), eq(JsonNode.class))).thenReturn(currentNode);
        when(patch.apply(currentNode)).thenThrow(new com.github.fge.jsonpatch.JsonPatchException("patch error"));

        assertThrows(IllegalArgumentException.class, () -> loanService.patchApplication(1L, patch));
    }

    // ── reviewApplication – approval ─────────────────────────────────────────

    @Test
    void reviewApplication_approval_setsApprovedStatusAndGeneratesSchedule() {
        LoanReviewRequest request = new LoanReviewRequest();
        request.setApproved(true);
        request.setAmountApproved(BigDecimal.valueOf(5000));
        request.setInterestRate(BigDecimal.valueOf(6.0));
        request.setReviewedBy(5L);

        pendingLoan.setTermMonths(3); // small number for manageable schedule generation

        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(pendingLoan));
        when(loanApplicationRepository.save(any())).thenReturn(pendingLoan);
        when(repaymentScheduleRepository.saveAll(any())).thenReturn(List.of());

        LoanApplicationResponse approvedResponse = new LoanApplicationResponse();
        approvedResponse.setId(1L);
        approvedResponse.setStatus("APPROVED");
        when(modelMapper.map(any(LoanApplication.class), eq(LoanApplicationResponse.class)))
                .thenReturn(approvedResponse);

        LoanApplicationResponse result = loanService.reviewApplication(1L, request);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(loanApplicationRepository).save(argThat(l ->
                l.getStatus() == LoanApplication.LoanStatus.APPROVED));
        verify(repaymentScheduleRepository).saveAll(argThat(schedules ->
                ((List<?>) schedules).size() == 3));
    }

    @Test
    void reviewApplication_approval_withoutAmountApproved_throwsIllegalArgumentException() {
        LoanReviewRequest request = new LoanReviewRequest();
        request.setApproved(true);
        request.setAmountApproved(null);
        request.setInterestRate(null);
        request.setReviewedBy(5L);

        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(pendingLoan));

        assertThrows(IllegalArgumentException.class,
                () -> loanService.reviewApplication(1L, request));
    }

    @Test
    void reviewApplication_rejection_setsRejectedStatus() {
        LoanReviewRequest request = new LoanReviewRequest();
        request.setApproved(false);
        request.setRejectionReason("Insufficient income");
        request.setReviewedBy(5L);

        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(pendingLoan));
        when(loanApplicationRepository.save(any())).thenReturn(pendingLoan);

        LoanApplicationResponse rejectedResponse = new LoanApplicationResponse();
        rejectedResponse.setId(1L);
        rejectedResponse.setStatus("REJECTED");
        when(modelMapper.map(any(LoanApplication.class), eq(LoanApplicationResponse.class)))
                .thenReturn(rejectedResponse);

        LoanApplicationResponse result = loanService.reviewApplication(1L, request);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        verify(loanApplicationRepository).save(argThat(l ->
                l.getStatus() == LoanApplication.LoanStatus.REJECTED));
    }

    @Test
    void reviewApplication_whenAlreadyReviewed_throwsIllegalStateException() {
        pendingLoan.setStatus(LoanApplication.LoanStatus.APPROVED);
        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(pendingLoan));

        LoanReviewRequest request = new LoanReviewRequest();
        request.setApproved(true);
        request.setReviewedBy(5L);

        assertThrows(IllegalStateException.class,
                () -> loanService.reviewApplication(1L, request));
    }

    // ── getRepaymentSchedule ──────────────────────────────────────────────────

    @Test
    void getRepaymentSchedule_whenLoanExists_returnsSchedules() {
        RepaymentSchedule schedule = new RepaymentSchedule();
        schedule.setId(1L);
        schedule.setLoanApplication(pendingLoan);
        schedule.setInstallmentNumber(1);
        schedule.setDueDate(LocalDate.now().plusMonths(1));
        schedule.setAmountDue(BigDecimal.valueOf(450));
        schedule.setAmountPaid(BigDecimal.ZERO);
        schedule.setStatus(RepaymentSchedule.InstallmentStatus.PENDING);

        when(loanApplicationRepository.findById(1L)).thenReturn(Optional.of(pendingLoan));
        when(repaymentScheduleRepository.findByLoanApplicationId(1L)).thenReturn(List.of(schedule));

        List<RepaymentScheduleResponse> result = loanService.getRepaymentSchedule(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInstallmentNumber()).isEqualTo(1);
    }

    @Test
    void getRepaymentSchedule_whenLoanNotFound_throwsResourceNotFoundException() {
        when(loanApplicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> loanService.getRepaymentSchedule(99L));
    }

    @Test
    void probeAccountServiceInstance_delegatesToClient() {
        when(accountProbeClient.probe("lb", null)).thenReturn(Map.of("mode", "lb"));

        Map<String, Object> result = loanService.probeAccountServiceInstance("lb", null);

        assertThat(result).containsEntry("mode", "lb");
        verify(accountProbeClient).probe("lb", null);
    }
}
