package com.nexusbank.userservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Local projection of account-service's aggregate stats response (F17). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountStatsView {

    private long totalAccounts;
    private long activeAccounts;
    private long checkingAccounts;
    private long savingsAccounts;
    private long foreignAccounts;
}
