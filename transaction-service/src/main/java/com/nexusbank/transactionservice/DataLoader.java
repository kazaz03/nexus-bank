package com.nexusbank.transactionservice;

import com.nexusbank.transactionservice.model.ExchangeRate;
import com.nexusbank.transactionservice.model.Transaction;
import com.nexusbank.transactionservice.repository.ExchangeRateRepository;
import com.nexusbank.transactionservice.repository.TransactionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DataLoader implements CommandLineRunner {

    private final TransactionRepository transactionRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public DataLoader(TransactionRepository transactionRepository,
                      ExchangeRateRepository exchangeRateRepository) {
        this.transactionRepository = transactionRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    public void run(String... args) {
        if (transactionRepository.count() > 0 || exchangeRateRepository.count() > 0) {
            return;
        }

        ExchangeRate eurToBam = new ExchangeRate();
        eurToBam.setFromCurrency("EUR");
        eurToBam.setToCurrency("BAM");
        eurToBam.setRate(new BigDecimal("1.955830"));
        eurToBam.setValidFrom(LocalDate.now().minusDays(30));
        eurToBam.setValidTo(LocalDate.now().plusDays(30));
        exchangeRateRepository.save(eurToBam);

        ExchangeRate bamToEur = new ExchangeRate();
        bamToEur.setFromCurrency("BAM");
        bamToEur.setToCurrency("EUR");
        bamToEur.setRate(new BigDecimal("0.511292"));
        bamToEur.setValidFrom(LocalDate.now().minusDays(30));
        bamToEur.setValidTo(LocalDate.now().plusDays(30));
        exchangeRateRepository.save(bamToEur);

        Transaction deposit = new Transaction();
        deposit.setAccountId(1L);
        deposit.setType(Transaction.TransactionType.DEPOSIT);
        deposit.setAmount(new BigDecimal("500.00"));
        deposit.setCurrency("BAM");
        deposit.setBalanceAfter(new BigDecimal("4342.50"));
        deposit.setReference("BRANCH-CASH-DEP-0001");
        deposit.setCreatedAt(LocalDateTime.now().minusDays(2));
        deposit.setCreatedBy(2L);
        deposit.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(deposit);

        Transaction transfer = new Transaction();
        transfer.setAccountId(1L);
        transfer.setType(Transaction.TransactionType.TRANSFER);
        transfer.setAmount(new BigDecimal("120.00"));
        transfer.setCurrency("BAM");
        transfer.setBalanceAfter(new BigDecimal("4222.50"));
        transfer.setCounterpartyIban("BA393000000111222333");
        transfer.setReference("APP-TRX-0002");
        transfer.setCreatedAt(LocalDateTime.now().minusDays(1));
        transfer.setCreatedBy(1L);
        transfer.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(transfer);

        Transaction fxTransfer = new Transaction();
        fxTransfer.setAccountId(3L);
        fxTransfer.setType(Transaction.TransactionType.FX_CONVERSION);
        fxTransfer.setAmount(new BigDecimal("250.00"));
        fxTransfer.setCurrency("EUR");
        fxTransfer.setBalanceAfter(new BigDecimal("4750.00"));
        fxTransfer.setCounterpartyIban("BA393000000123456789");
        fxTransfer.setReference("FX-TRX-0003");
        fxTransfer.setExchangeRate(eurToBam);
        fxTransfer.setCreatedAt(LocalDateTime.now().minusHours(8));
        fxTransfer.setCreatedBy(1L);
        fxTransfer.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(fxTransfer);

        System.out.println("Transaction Service - data loaded");
    }
}

