package com.nexusbank.loanservice.service;

import com.nexusbank.loanservice.dto.request.LoanApplicationRequest;
import com.nexusbank.loanservice.dto.request.LoanReviewRequest;
import com.nexusbank.loanservice.dto.response.LoanApplicationResponse;
import com.nexusbank.loanservice.dto.response.RepaymentScheduleResponse;
import com.nexusbank.loanservice.exception.ResourceNotFoundException;
import com.nexusbank.loanservice.model.LoanApplication;
import com.nexusbank.loanservice.model.RepaymentSchedule;
import com.nexusbank.loanservice.repository.LoanApplicationRepository;
import com.nexusbank.loanservice.repository.RepaymentScheduleRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class LoanService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final ModelMapper modelMapper;

    public LoanService(LoanApplicationRepository loanApplicationRepository,
                       RepaymentScheduleRepository repaymentScheduleRepository,
                       ModelMapper modelMapper) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public LoanApplicationResponse submitApplication(LoanApplicationRequest request) {
        LoanApplication application = new LoanApplication();
        application.setCustomerId(request.getCustomerId());
        application.setAccountId(request.getAccountId());
        application.setAmountRequested(request.getAmountRequested());
        application.setCurrency(request.getCurrency() != null ? request.getCurrency() : "BAM");
        application.setTermMonths(request.getTermMonths());
        application.setPurpose(request.getPurpose());
        application.setStatus(LoanApplication.LoanStatus.PENDING);
        application.setCreatedAt(LocalDateTime.now());

        loanApplicationRepository.save(application);
        return toResponse(application);
    }

    public LoanApplicationResponse getApplication(Long id) {
        return toResponse(findById(id));
    }

    public List<LoanApplicationResponse> getApplicationsByCustomer(Long customerId) {
        return loanApplicationRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<LoanApplicationResponse> getAllApplications() {
        return loanApplicationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
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
