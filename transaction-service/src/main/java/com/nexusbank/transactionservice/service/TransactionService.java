package com.nexusbank.transactionservice.service;

import com.nexusbank.transactionservice.dto.response.DailyVolumeResponse;
import com.nexusbank.transactionservice.dto.response.ExchangeRateResponse;
import com.nexusbank.transactionservice.dto.response.StatementResponse;
import com.nexusbank.transactionservice.dto.response.TransactionResponse;
import com.nexusbank.transactionservice.dto.response.TransactionStatsResponse;
import com.nexusbank.transactionservice.exception.ResourceNotFoundException;
import com.nexusbank.transactionservice.model.Transaction;
import com.nexusbank.transactionservice.repository.ExchangeRateRepository;
import com.nexusbank.transactionservice.repository.TransactionRepository;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final ModelMapper modelMapper;

    public TransactionService(TransactionRepository transactionRepository,
                              ExchangeRateRepository exchangeRateRepository,
                              ModelMapper modelMapper) {
        this.transactionRepository = transactionRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.modelMapper = modelMapper;
    }

    public TransactionResponse getTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
        return toResponse(transaction);
    }

    public Page<TransactionResponse> getTransactionHistory(
            Long accountId,
            String type,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {

        Transaction.TransactionType txType = parseType(type);
        boolean hasType = txType != null;
        boolean hasDateRange = from != null && to != null;

        Page<Transaction> page;
        if (hasType && hasDateRange) {
            page = transactionRepository.findByAccountIdAndTypeAndCreatedAtBetween(
                    accountId, txType, from, to, pageable);
        } else if (hasType) {
            page = transactionRepository.findByAccountIdAndType(accountId, txType, pageable);
        } else if (hasDateRange) {
            page = transactionRepository.findByAccountIdAndCreatedAtBetween(
                    accountId, from, to, pageable);
        } else {
            page = transactionRepository.findByAccountId(accountId, pageable);
        }

        return page.map(this::toResponse);
    }

    public StatementResponse getStatement(Long accountId, LocalDateTime from, LocalDateTime to) {
        List<Transaction> transactions = transactionRepository
                .findByAccountIdAndCreatedAtBetween(accountId, from, to);

        BigDecimal totalDeposited = transactions.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.DEPOSIT
                        || (t.getType() == Transaction.TransactionType.TRANSFER
                        && t.getCounterpartyIban() == null))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWithdrawn = transactions.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.WITHDRAWAL
                        || (t.getType() == Transaction.TransactionType.TRANSFER
                        && t.getCounterpartyIban() != null))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal closingBalance = transactions.isEmpty() ? BigDecimal.ZERO
                : transactions.get(transactions.size() - 1).getBalanceAfter();

        BigDecimal openingBalance = closingBalance
                .subtract(totalDeposited)
                .add(totalWithdrawn);

        List<TransactionResponse> txResponses = transactions.stream()
                .map(this::toResponse)
                .toList();

        StatementResponse statement = new StatementResponse();
        statement.setAccountId(accountId);
        statement.setFromDate(from);
        statement.setToDate(to);
        statement.setOpeningBalance(openingBalance);
        statement.setTotalDeposited(totalDeposited);
        statement.setTotalWithdrawn(totalWithdrawn);
        statement.setClosingBalance(closingBalance);
        statement.setTransactions(txResponses);
        return statement;
    }

    /** Aggregate transaction metrics for the admin dashboard (F17). */
    public TransactionStatsResponse getStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        long transactionsToday = transactionRepository.countByCreatedAtBetween(startOfToday, now);
        long totalTransactions = transactionRepository.count();

        // Build a continuous 7-day series (oldest → today), seeding every day
        // with zeros so the chart never has gaps even on quiet days.
        LocalDate windowStart = today.minusDays(6);
        Map<LocalDate, long[]> counts = new TreeMap<>();
        Map<LocalDate, BigDecimal> sums = new TreeMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = windowStart.plusDays(i);
            counts.put(day, new long[]{0L});
            sums.put(day, BigDecimal.ZERO);
        }

        List<Transaction> recent = transactionRepository.findByCreatedAtBetween(
                windowStart.atStartOfDay(), today.atTime(LocalTime.MAX));
        for (Transaction tx : recent) {
            LocalDate day = tx.getCreatedAt().toLocalDate();
            if (counts.containsKey(day)) {
                counts.get(day)[0]++;
                sums.put(day, sums.get(day).add(tx.getAmount()));
            }
        }

        List<DailyVolumeResponse> last7Days = new ArrayList<>();
        for (LocalDate day : counts.keySet()) {
            last7Days.add(new DailyVolumeResponse(
                    day.toString(), counts.get(day)[0], sums.get(day)));
        }

        return new TransactionStatsResponse(transactionsToday, totalTransactions, last7Days);
    }

    public List<ExchangeRateResponse> getCurrentExchangeRates() {
        return exchangeRateRepository.findAll().stream()
                .map(rate -> modelMapper.map(rate, ExchangeRateResponse.class))
                .toList();
    }

    private Transaction.TransactionType parseType(String type) {
        if (type == null || type.isBlank()) return null;
        try {
            return Transaction.TransactionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction type: " + type);
        }
    }

    private TransactionResponse toResponse(Transaction tx) {
        TransactionResponse response = new TransactionResponse();
        response.setId(tx.getId());
        response.setAccountId(tx.getAccountId());
        response.setType(tx.getType().name());
        response.setAmount(tx.getAmount());
        response.setCurrency(tx.getCurrency());
        response.setBalanceAfter(tx.getBalanceAfter());
        response.setCounterpartyIban(tx.getCounterpartyIban());
        response.setReference(tx.getReference());
        response.setExchangeRateId(tx.getExchangeRate() != null ? tx.getExchangeRate().getId() : null);
        response.setCreatedAt(tx.getCreatedAt());
        response.setCreatedBy(tx.getCreatedBy());
        response.setStatus(tx.getStatus().name());
        return response;
    }
}
