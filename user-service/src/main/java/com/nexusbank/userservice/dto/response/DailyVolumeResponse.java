package com.nexusbank.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One day's transaction volume in the admin dashboard 7-day chart (F17). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyVolumeResponse {

    private String date;
    private long count;
    private BigDecimal totalAmount;
}
