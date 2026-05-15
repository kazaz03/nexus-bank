package com.nexusbank.loanservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Published by account-service when the loan disbursement was successfully
 * credited to the customer's account.
 *
 * Consumed by loan-service to move the loan into the final DISBURSED state.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DisbursementCompletedEvent {
    private Long loanApplicationId;
    private Long accountId;
    private BigDecimal amountCredited;
    private BigDecimal balanceAfter;
}
