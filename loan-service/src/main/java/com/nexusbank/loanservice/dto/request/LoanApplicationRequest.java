package com.nexusbank.loanservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanApplicationRequest {

    @NotNull
    private Long customerId;

    @NotNull
    private Long accountId;

    @NotNull
    @DecimalMin(value = "100.00", message = "Loan amount must be at least 100")
    private BigDecimal amountRequested;

    private String currency = "BAM";

    @NotNull
    @Min(value = 1, message = "Term must be at least 1 month")
    private Integer termMonths;

    @NotBlank
    private String purpose;
}
