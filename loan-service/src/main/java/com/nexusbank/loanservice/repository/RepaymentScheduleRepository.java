package com.nexusbank.loanservice.repository;

import com.nexusbank.loanservice.model.RepaymentSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, Long> {

    @Override
    @EntityGraph(attributePaths = {"loanApplication"})
    Optional<RepaymentSchedule> findById(Long id);

    @EntityGraph(attributePaths = {"loanApplication"})
    List<RepaymentSchedule> findByLoanApplicationId(Long loanApplicationId);
}

