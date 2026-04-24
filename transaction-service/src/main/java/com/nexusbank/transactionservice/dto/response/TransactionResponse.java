package com.nexusbank.transactionservice.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionResponse {

    private Long id;
    private Long accountId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balanceAfter;
    private String counterpartyIban;
    private String reference;
    private Long exchangeRateId;
    private LocalDateTime createdAt;
    private Long createdBy;
    private String status;
}
