package com.nexusbank.accountservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    private Long accountId;
    private String iban;
    private String currency;
    private BigDecimal balance;
    private BigDecimal overdraftLimit;
    private BigDecimal availableBalance;
    private String status;
}
