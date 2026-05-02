package com.nexusbank.accountservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response returned after a successful debit/credit operation to inform
 * the caller of the new balance and account currency.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdateResponse {

    private Long accountId;
    private String iban;
    private String currency;
    private BigDecimal newBalance;
}
