package com.nexusbank.accountservice.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AccountResponse {

    private Long id;
    private Long customerId;
    private String iban;
    private String accountType;
    private String currency;
    private BigDecimal balance;
    private BigDecimal overdraftLimit;
    private BigDecimal interestRate;
    private String status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime closedAt;
    private Long closedBy;
}
