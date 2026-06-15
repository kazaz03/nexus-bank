package com.nexusbank.loanservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregate loan metrics for the admin dashboard (F17), with the count of
 * applications waiting for review highlighted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanStatsResponse {

    private long totalLoans;
    private long pendingLoans;
    private long approvedLoans;
    private long disbursedLoans;
    private long rejectedLoans;
}
