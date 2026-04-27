package com.nexusbank.loanservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.nexusbank.loanservice.client.AccountProbeClient;
import com.nexusbank.loanservice.dto.request.LoanApplicationRequest;
import com.nexusbank.loanservice.dto.request.LoanApplicationPatchRequest;
import com.nexusbank.loanservice.dto.request.LoanReviewRequest;
import com.nexusbank.loanservice.dto.response.LoanApplicationResponse;
import com.nexusbank.loanservice.dto.response.RepaymentScheduleResponse;
import com.nexusbank.loanservice.exception.ResourceNotFoundException;
import com.nexusbank.loanservice.model.LoanApplication;
import com.nexusbank.loanservice.model.RepaymentSchedule;
import com.nexusbank.loanservice.repository.LoanApplicationRepository;
import com.nexusbank.loanservice.repository.RepaymentScheduleRepository;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LoanService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;
    private final AccountProbeClient accountProbeClient;

    public LoanService(LoanApplicationRepository loanApplicationRepository,
                       RepaymentScheduleRepository repaymentScheduleRepository,
                       ModelMapper modelMapper,
                       ObjectMapper objectMapper,
                       AccountProbeClient accountProbeClient) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
        this.modelMapper = modelMapper;
        this.objectMapper = objectMapper;
        this.accountProbeClient = accountProbeClient;
    }

    @Transactional
    public LoanApplicationResponse submitApplication(LoanApplicationRequest request) {
        LoanApplication application = createPendingApplication(request);

        loanApplicationRepository.save(application);
        return toResponse(application);
    }

    @Transactional
    public List<LoanApplicationResponse> submitApplicationsBatch(List<LoanApplicationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Batch request must contain at least one application");
        }

        List<LoanApplication> applications = requests.stream()
                .map(this::createPendingApplication)
                .toList();

        loanApplicationRepository.saveAll(applications);
        return applications.stream()
                .map(this::toResponse)
                .toList();
    }

    public LoanApplicationResponse getApplication(Long id) {
        return toResponse(findById(id));
    }

    public List<LoanApplicationResponse> getApplicationsByCustomer(Long customerId) {
        return loanApplicationRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Page<LoanApplicationResponse> getAllApplications(
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long customerId,
            String status,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("minAmount cannot be greater than maxAmount");
        }

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        LoanApplication.LoanStatus parsedStatus = parseStatus(status);

        return loanApplicationRepository.searchApplications(customerId, parsedStatus, minAmount, maxAmount, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public LoanApplicationResponse patchApplication(Long id, JsonPatch patch) {
        LoanApplication application = findById(id);
        if (application.getStatus() != LoanApplication.LoanStatus.PENDING) {
            throw new IllegalStateException("Only PENDING applications can be patched");
        }

        LoanApplicationPatchRequest currentState = new LoanApplicationPatchRequest();
        currentState.setAmountRequested(application.getAmountRequested());
        currentState.setCurrency(application.getCurrency());
        currentState.setTermMonths(application.getTermMonths());
        currentState.setPurpose(application.getPurpose());

        LoanApplicationPatchRequest patchedState;
        try {
            JsonNode patchedNode = patch.apply(objectMapper.convertValue(currentState, JsonNode.class));
            patchedState = objectMapper.treeToValue(patchedNode, LoanApplicationPatchRequest.class);
        } catch (JsonPatchException | JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON Patch document", ex);
        }
        validatePatchedState(patchedState);

        application.setAmountRequested(patchedState.getAmountRequested());
        application.setCurrency(patchedState.getCurrency().trim().toUpperCase());
        application.setTermMonths(patchedState.getTermMonths());
        application.setPurpose(patchedState.getPurpose().trim());

        loanApplicationRepository.save(application);
        return toResponse(application);
    }

    @Transactional
    public LoanApplicationResponse reviewApplication(Long id, LoanReviewRequest request) {
        LoanApplication application = findById(id);

        if (application.getStatus() != LoanApplication.LoanStatus.PENDING) {
            throw new IllegalStateException("Only PENDING applications can be reviewed");
        }

        application.setReviewedBy(request.getReviewedBy());
        application.setReviewedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(request.getApproved())) {
            if (request.getAmountApproved() == null || request.getInterestRate() == null) {
                throw new IllegalArgumentException("Amount approved and interest rate are required for approval");
            }
            application.setStatus(LoanApplication.LoanStatus.APPROVED);
            application.setAmountApproved(request.getAmountApproved());
            application.setInterestRate(request.getInterestRate());
            loanApplicationRepository.save(application);
            generateRepaymentSchedule(application);
        } else {
            application.setStatus(LoanApplication.LoanStatus.REJECTED);
            application.setRejectionReason(request.getRejectionReason());
            loanApplicationRepository.save(application);
        }

        return toResponse(application);
    }

    public List<RepaymentScheduleResponse> getRepaymentSchedule(Long loanId) {
        findById(loanId);
        return repaymentScheduleRepository.findByLoanApplicationId(loanId).stream()
                .map(this::toScheduleResponse)
                .toList();
    }

    public Map<String, Object> probeAccountServiceInstance(String mode, String directBaseUrl) {
        return accountProbeClient.probe(mode, directBaseUrl);
    }

    private LoanApplication createPendingApplication(LoanApplicationRequest request) {
        LoanApplication application = new LoanApplication();
        application.setCustomerId(request.getCustomerId());
        application.setAccountId(request.getAccountId());
        application.setAmountRequested(request.getAmountRequested());
        application.setCurrency(request.getCurrency() != null ? request.getCurrency().trim().toUpperCase() : "BAM");
        application.setTermMonths(request.getTermMonths());
        application.setPurpose(request.getPurpose());
        application.setStatus(LoanApplication.LoanStatus.PENDING);
        application.setCreatedAt(LocalDateTime.now());
        return application;
    }

    private LoanApplication.LoanStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return LoanApplication.LoanStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status value: " + status);
        }
    }

    private void validatePatchedState(LoanApplicationPatchRequest request) {
        if (request.getAmountRequested() == null || request.getAmountRequested().compareTo(new BigDecimal("100.00")) < 0) {
            throw new IllegalArgumentException("Loan amount must be at least 100");
        }
        if (request.getTermMonths() == null || request.getTermMonths() < 1) {
            throw new IllegalArgumentException("Term must be at least 1 month");
        }
        if (request.getPurpose() == null || request.getPurpose().isBlank()) {
            throw new IllegalArgumentException("Purpose must not be blank");
        }
        if (request.getCurrency() == null || request.getCurrency().isBlank() || request.getCurrency().trim().length() != 3) {
            throw new IllegalArgumentException("Currency must be a 3-letter code");
        }
    }

    private void generateRepaymentSchedule(LoanApplication loan) {
        BigDecimal principal = loan.getAmountApproved();
        BigDecimal annualRate = loan.getInterestRate();
        int months = loan.getTermMonths();

        BigDecimal monthlyRate = annualRate
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        BigDecimal installmentAmount;
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            installmentAmount = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        } else {
            // PMT formula: P * r * (1+r)^n / ((1+r)^n - 1)
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal pow = onePlusR.pow(months);
            installmentAmount = principal
                    .multiply(monthlyRate)
                    .multiply(pow)
                    .divide(pow.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
        }

        List<RepaymentSchedule> schedules = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            RepaymentSchedule schedule = new RepaymentSchedule();
            schedule.setLoanApplication(loan);
            schedule.setInstallmentNumber(i);
            schedule.setDueDate(LocalDate.now().plusMonths(i));
            schedule.setAmountDue(installmentAmount);
            schedule.setAmountPaid(BigDecimal.ZERO);
            schedule.setStatus(RepaymentSchedule.InstallmentStatus.PENDING);
            schedules.add(schedule);
        }
        repaymentScheduleRepository.saveAll(schedules);
    }

    private LoanApplication findById(Long id) {
        return loanApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + id));
    }

    private LoanApplicationResponse toResponse(LoanApplication loan) {
        LoanApplicationResponse response = modelMapper.map(loan, LoanApplicationResponse.class);
        response.setStatus(loan.getStatus().name());
        return response;
    }

    private RepaymentScheduleResponse toScheduleResponse(RepaymentSchedule schedule) {
        RepaymentScheduleResponse response = new RepaymentScheduleResponse();
        response.setId(schedule.getId());
        response.setLoanApplicationId(schedule.getLoanApplication().getId());
        response.setInstallmentNumber(schedule.getInstallmentNumber());
        response.setDueDate(schedule.getDueDate());
        response.setAmountDue(schedule.getAmountDue());
        response.setAmountPaid(schedule.getAmountPaid());
        response.setStatus(schedule.getStatus().name());
        response.setPaidAt(schedule.getPaidAt());
        return response;
    }
}
