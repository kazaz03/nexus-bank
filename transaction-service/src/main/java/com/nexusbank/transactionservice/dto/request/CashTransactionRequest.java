package com.nexusbank.transactionservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request issued by a teller to deposit cash into, or withdraw cash from,
 * a customer's account at the counter (F10).
 *
 * The same shape is used for both DEPOSIT and WITHDRAWAL — the operation is
 * determined by the endpoint that receives it.
 */
@Data
public class CashTransactionRequest {

    /** Primary key of the account the cash movement applies to. */
    @NotNull
    private Long accountId;

    /** Cash amount, expressed in the account's own currency. */
    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    /** Optional human-readable note (e.g. "Counter deposit — Sarajevo Centar"). */
    @Size(max = 100)
    private String reference;

    /** User id of the teller performing the operation (for audit). */
    private Long performedBy;
}
