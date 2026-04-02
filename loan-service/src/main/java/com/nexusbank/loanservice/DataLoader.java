package com.nexusbank.loanservice;

import com.nexusbank.loanservice.model.LoanApplication;
import com.nexusbank.loanservice.model.RepaymentSchedule;
import com.nexusbank.loanservice.repository.LoanApplicationRepository;
import com.nexusbank.loanservice.repository.RepaymentScheduleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DataLoader implements CommandLineRunner {

    private final LoanApplicationRepository loanApplicationRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;

    public DataLoader(LoanApplicationRepository loanApplicationRepository,
                      RepaymentScheduleRepository repaymentScheduleRepository) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
    }

    @Override
    public void run(String... args) {
        if (loanApplicationRepository.count() > 0 || repaymentScheduleRepository.count() > 0) {
            return;
        }

        LoanApplication approvedLoan = new LoanApplication();
        approvedLoan.setCustomerId(1L);
        approvedLoan.setAccountId(1L);
        approvedLoan.setAmountRequested(new BigDecimal("7000.00"));
        approvedLoan.setAmountApproved(new BigDecimal("6500.00"));
        approvedLoan.setCurrency("BAM");
        approvedLoan.setInterestRate(new BigDecimal("6.20"));
        approvedLoan.setTermMonths(24);
        approvedLoan.setPurpose("Home renovation");
        approvedLoan.setStatus(LoanApplication.LoanStatus.APPROVED);
        approvedLoan.setReviewedBy(3L);
        approvedLoan.setReviewedAt(LocalDateTime.now().minusDays(3));
        approvedLoan.setCreatedAt(LocalDateTime.now().minusDays(5));
        loanApplicationRepository.save(approvedLoan);

        LoanApplication pendingLoan = new LoanApplication();
        pendingLoan.setCustomerId(1L);
        pendingLoan.setAccountId(1L);
        pendingLoan.setAmountRequested(new BigDecimal("3000.00"));
        pendingLoan.setCurrency("BAM");
        pendingLoan.setInterestRate(new BigDecimal("7.10"));
        pendingLoan.setTermMonths(12);
        pendingLoan.setPurpose("Medical expenses");
        pendingLoan.setStatus(LoanApplication.LoanStatus.PENDING);
        pendingLoan.setCreatedAt(LocalDateTime.now().minusHours(10));
        loanApplicationRepository.save(pendingLoan);

        RepaymentSchedule installment1 = new RepaymentSchedule();
        installment1.setLoanApplication(approvedLoan);
        installment1.setInstallmentNumber(1);
        installment1.setDueDate(LocalDate.now().minusMonths(1));
        installment1.setAmountDue(new BigDecimal("287.50"));
        installment1.setAmountPaid(new BigDecimal("287.50"));
        installment1.setStatus(RepaymentSchedule.InstallmentStatus.PAID);
        installment1.setPaidAt(LocalDateTime.now().minusMonths(1).plusDays(1));
        repaymentScheduleRepository.save(installment1);

        RepaymentSchedule installment2 = new RepaymentSchedule();
        installment2.setLoanApplication(approvedLoan);
        installment2.setInstallmentNumber(2);
        installment2.setDueDate(LocalDate.now().plusDays(10));
        installment2.setAmountDue(new BigDecimal("287.50"));
        installment2.setAmountPaid(BigDecimal.ZERO);
        installment2.setStatus(RepaymentSchedule.InstallmentStatus.PENDING);
        repaymentScheduleRepository.save(installment2);

        System.out.println("Loan Service - data loaded");
    }
}

