package com.nexusbank.transactionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of a successful transfer. Contains a summary of both legs
 * (DEBIT + CREDIT) and exchange-rate metadata when applicable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    /** Generated reference shared by both transaction records. */
    private String reference;

    private Long debitTransactionId;
    private Long creditTransactionId;

    private String sourceIban;
    private String targetIban;

    private BigDecimal sourceAmount;
    private String sourceCurrency;

    private BigDecimal targetAmount;
    private String targetCurrency;

    /** Exchange rate applied when source.currency != target.currency. */
    private BigDecimal exchangeRate;

    private BigDecimal sourceBalanceAfter;
    private BigDecimal targetBalanceAfter;

    private LocalDateTime executedAt;

    private String status;
}
