package com.nexusbank.loanservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by account-service when the loan disbursement could not be applied
 * (account not found, account closed, etc.).
 *
 * Consumed by loan-service to roll the loan back to REJECTED — the inverse
 * action that keeps the two services consistent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DisbursementFailedEvent {
    private Long loanApplicationId;
    private Long accountId;
    private String reason;
}
