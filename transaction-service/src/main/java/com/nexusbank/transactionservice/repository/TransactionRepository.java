package com.nexusbank.transactionservice.repository;

import com.nexusbank.transactionservice.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountId(Long accountId);

    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    Page<Transaction> findByAccountIdAndType(
            Long accountId,
            Transaction.TransactionType type,
            Pageable pageable);

    Page<Transaction> findByAccountIdAndCreatedAtBetween(
            Long accountId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    Page<Transaction> findByAccountIdAndTypeAndCreatedAtBetween(
            Long accountId,
            Transaction.TransactionType type,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    List<Transaction> findByAccountIdAndCreatedAtBetween(
            Long accountId,
            LocalDateTime from,
            LocalDateTime to);
}
