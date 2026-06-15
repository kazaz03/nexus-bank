package com.nexusbank.accountservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregate account metrics exposed over the internal API for the admin
 * dashboard (F17): total/active account counts and a breakdown by account
 * type across active accounts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatsResponse {

    private long totalAccounts;
    private long activeAccounts;
    private long checkingAccounts;
    private long savingsAccounts;
    private long foreignAccounts;
}
