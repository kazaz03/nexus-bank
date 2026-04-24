package com.nexusbank.userservice.repository;

import com.nexusbank.userservice.model.Customer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Override
    @EntityGraph(attributePaths = {"user"})
    List<Customer> findAll();

    @Override
    @EntityGraph(attributePaths = {"user"})
    Optional<Customer> findById(Long id);

    @EntityGraph(attributePaths = {"user"})
    Optional<Customer> findByIdCardNumber(String idCardNumber);
}