package com.nexusbank.loanservice.repository;

import com.nexusbank.loanservice.model.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    List<LoanApplication> findByCustomerId(Long customerId);

    @Query("""
            select l
            from LoanApplication l
            where (:customerId is null or l.customerId = :customerId)
              and (:status is null or l.status = :status)
              and (:minAmount is null or l.amountRequested >= :minAmount)
              and (:maxAmount is null or l.amountRequested <= :maxAmount)
            """)
    Page<LoanApplication> searchApplications(
            @Param("customerId") Long customerId,
            @Param("status") LoanApplication.LoanStatus status,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable);
}

