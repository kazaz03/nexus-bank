package com.nexusbank.loanservice.repository;

import com.nexusbank.loanservice.model.RepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, Long> {
    List<RepaymentSchedule> findByLoanApplicationId(Long loanApplicationId);
}

