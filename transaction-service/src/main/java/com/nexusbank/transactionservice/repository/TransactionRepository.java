package com.nexusbank.transactionservice.repository;

import com.nexusbank.transactionservice.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
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

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * Returns per-day count and total amount for the given window — a single
     * aggregate SQL query instead of loading every transaction row into memory.
     * Used by the admin stats dashboard (F17).
     */
    @Query(value = "SELECT DATE(t.created_at) AS day, COUNT(*) AS txCount, SUM(t.amount) AS totalAmount " +
                   "FROM transactions t " +
                   "WHERE t.created_at BETWEEN :from AND :to " +
                   "GROUP BY DATE(t.created_at)",
           nativeQuery = true)
    List<DailyStats> getDailyStats(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    /** Projection for the native daily-aggregate query. */
    interface DailyStats {
        Date getDay();
        Long getTxCount();
        BigDecimal getTotalAmount();
    }
}
