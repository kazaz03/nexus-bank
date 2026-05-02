package com.nexusbank.transactionservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
@Data
public class TransferRequest {

    /** IBAN of the account to debit. */
    @NotBlank
    @Pattern(regexp = "BA\\d{18}", message = "Source IBAN must be in format BA + 18 digits (20 chars total)")
    private String sourceIban;

    /** IBAN of the account to credit. */
    @NotBlank
    @Pattern(regexp = "BA\\d{18}", message = "Target IBAN must be in format BA + 18 digits (20 chars total)")
    private String targetIban;

    /** Amount expressed in source-account currency. */
    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    /** Optional human-readable reference (e.g. "Rent March 2026"). */
    @Size(max = 100)
    private String reference;

    @NotNull
    private Long initiatedBy;
}
