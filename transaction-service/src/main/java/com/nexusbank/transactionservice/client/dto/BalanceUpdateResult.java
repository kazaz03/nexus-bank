package com.nexusbank.transactionservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Result returned from Account Service after a successful balance change.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BalanceUpdateResult {

    private Long accountId;
    private String iban;
    private String currency;
    private BigDecimal newBalance;
}
