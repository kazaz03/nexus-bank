package com.nexusbank.accountservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
