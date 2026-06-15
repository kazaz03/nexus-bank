package com.nexusbank.transactionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregate transaction metrics for the admin dashboard (F17): the number of
 * transactions recorded today and a per-day volume series for the last 7 days.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatsResponse {

    private long transactionsToday;
    private long totalTransactions;
    private List<DailyVolumeResponse> last7Days;
}
