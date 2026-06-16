package com.nexusbank.loanservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanReviewRequest {

    @NotNull
    private Boolean approved;

    /**
     * Required when approved = true. Must be positive so that the resulting
     * LoanApprovedEvent credits (not debits) the customer's account.
     */
    @Positive(message = "Amount approved must be greater than zero")
    private BigDecimal amountApproved;

    /**
     * Annual interest rate. Zero is allowed (interest-free loan); negative is
     * not. Required when approved = true.
     */
    @DecimalMin(value = "0.00", message = "Interest rate must be zero or positive")
    private BigDecimal interestRate;

    private String rejectionReason;
}
