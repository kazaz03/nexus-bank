package com.nexusbank.loanservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanReviewRequest {

    @NotNull
    private Boolean approved;

    private BigDecimal amountApproved;

    private BigDecimal interestRate;

    private String rejectionReason;

    @NotNull
    private Long reviewedBy;
}
