package com.nexusbank.transactionservice.repository;

import com.nexusbank.transactionservice.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Override
    @EntityGraph(attributePaths = {"exchangeRate"})
    Optional<Transaction> findById(Long id);

    @EntityGraph(attributePaths = {"exchangeRate"})
    List<Transaction> findByAccountId(Long accountId);

    @EntityGraph(attributePaths = {"exchangeRate"})
    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    @EntityGraph(attributePaths = {"exchangeRate"})
    Page<Transaction> findByAccountIdAndType(
            Long accountId,
            Transaction.TransactionType type,
            Pageable pageable);

    @EntityGraph(attributePaths = {"exchangeRate"})
    Page<Transaction> findByAccountIdAndCreatedAtBetween(
            Long accountId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    @EntityGraph(attributePaths = {"exchangeRate"})
    Page<Transaction> findByAccountIdAndTypeAndCreatedAtBetween(
            Long accountId,
            Transaction.TransactionType type,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    @EntityGraph(attributePaths = {"exchangeRate"})
    List<Transaction> findByAccountIdAndCreatedAtBetween(
            Long accountId,
            LocalDateTime from,
            LocalDateTime to);
}
