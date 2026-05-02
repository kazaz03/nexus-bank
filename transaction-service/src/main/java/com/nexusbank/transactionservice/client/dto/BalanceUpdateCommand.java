package com.nexusbank.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Outbound payload sent to Account Service's /debit and /credit endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdateCommand {

    private BigDecimal amount;
    private String reference;
    private String idempotencyKey;
}
