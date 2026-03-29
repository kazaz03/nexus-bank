package com.nexusbank.userservice.repository;

import com.nexusbank.userservice.model.LoanOfficer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanOfficerRepository extends JpaRepository<LoanOfficer, Long> {
}