package com.nexusbank.loanservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Published by loan-service after a loan application is approved.
 * Consumed by account-service to credit the disbursement amount.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoanApprovedEvent {
    private Long loanApplicationId;
    private Long customerId;
    private Long accountId;
    private BigDecimal amountApproved;
    private String currency;
}
