package com.nexusbank.transactionservice.repository;

import com.nexusbank.transactionservice.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    List<ExchangeRate> findByFromCurrencyAndToCurrencyAndValidFromLessThanEqualAndValidToGreaterThanEqual(
            String fromCurrency,
            String toCurrency,
            LocalDate validFrom,
            LocalDate validTo
    );
}

