package com.nexusbank.transactionservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Read-only projection of an Account record as returned by the Account
 * Service over its internal API. Defined locally in this service to keep
 * Transaction Service decoupled from Account Service's internal entity
 * layout.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountView {

    private Long id;
    private Long customerId;
    private String iban;
    private String accountType;
    private String currency;
    private BigDecimal balance;
    private BigDecimal overdraftLimit;
    private String status;
}
