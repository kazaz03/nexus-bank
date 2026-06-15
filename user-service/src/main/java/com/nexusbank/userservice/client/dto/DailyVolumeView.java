package com.nexusbank.userservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/** Local projection of one day's transaction volume (F17). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyVolumeView {

    private String date;
    private long count;
    private BigDecimal totalAmount;
}
