package com.nexusbank.userservice.repository;

import com.nexusbank.userservice.model.Teller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TellerRepository extends JpaRepository<Teller, Long> {
}