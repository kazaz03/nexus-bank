package com.nexusbank.userservice.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full statistics payload for the admin dashboard (F17). Combines user
 * counters owned by user-service with aggregates fetched from the account,
 * transaction and loan services.
 */
@Data
@NoArgsConstructor
public class AdminStatsResponse {

    // ── Users (owned by user-service) ───────────────────────────────
    private long totalUsers;
    private long totalCustomers;
    private long totalTellers;
    private long totalLoanOfficers;
    private long totalAdmins;
    private long activeUsers;

    // ── Accounts (account-service) ──────────────────────────────────
    private long totalAccounts;
    private long activeAccounts;
    private long checkingAccounts;
    private long savingsAccounts;
    private long foreignAccounts;

    // ── Transactions (transaction-service) ──────────────────────────
    private long transactionsToday;
    private long totalTransactions;
    private List<DailyVolumeResponse> volumeLast7Days;

    // ── Loans (loan-service) ────────────────────────────────────────
    private long totalLoans;
    private long pendingLoans;
    private long approvedLoans;
    private long disbursedLoans;
    private long rejectedLoans;
}
