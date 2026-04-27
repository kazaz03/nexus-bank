package com.nexusbank.loanservice.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanApplicationPatchRequest {

    private BigDecimal amountRequested;
    private String currency;
    private Integer termMonths;
    private String purpose;
}
