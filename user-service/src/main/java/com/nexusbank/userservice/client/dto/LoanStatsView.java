package com.nexusbank.userservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Local projection of loan-service's aggregate stats response (F17). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoanStatsView {

    private long totalLoans;
    private long pendingLoans;
    private long approvedLoans;
    private long disbursedLoans;
    private long rejectedLoans;
}
