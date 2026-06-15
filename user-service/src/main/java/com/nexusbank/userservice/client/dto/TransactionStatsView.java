package com.nexusbank.userservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/** Local projection of transaction-service's aggregate stats response (F17). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionStatsView {

    private long transactionsToday;
    private long totalTransactions;
    private List<DailyVolumeView> last7Days;
}
