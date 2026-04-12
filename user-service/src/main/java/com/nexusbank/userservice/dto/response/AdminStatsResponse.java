package com.nexusbank.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {

    private long totalUsers;
    private long totalCustomers;
    private long totalTellers;
    private long totalLoanOfficers;
    private long totalAdmins;
    private long activeUsers;
}
