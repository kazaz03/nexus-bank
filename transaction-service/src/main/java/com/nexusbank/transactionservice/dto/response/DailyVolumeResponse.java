package com.nexusbank.transactionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One day's transaction volume for the admin dashboard 7-day chart (F17).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyVolumeResponse {

    /** ISO date, e.g. 2026-06-15. */
    private String date;
    private long count;
    private BigDecimal totalAmount;
}
