package com.nexusbank.loanservice.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LoanApplicationResponse {

    private Long id;
    private Long customerId;
    private Long accountId;
    private BigDecimal amountRequested;
    private BigDecimal amountApproved;
    private String currency;
    private BigDecimal interestRate;
    private Integer termMonths;
    private String purpose;
    private String status;
    private String rejectionReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
