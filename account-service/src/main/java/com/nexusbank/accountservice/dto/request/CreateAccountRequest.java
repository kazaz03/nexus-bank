package com.nexusbank.accountservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAccountRequest {

    @NotNull
    private Long customerId;

    @NotNull
    private String accountType;

    private String currency = "BAM";

    private BigDecimal overdraftLimit;

    private BigDecimal interestRate;

    private Long createdBy;
}
