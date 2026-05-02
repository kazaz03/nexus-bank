package com.nexusbank.accountservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Inbound request used by other microservices (e.g. Transaction Service)
 * to perform a debit or credit on an account.
 *
 * The same DTO is used for both operations — the operation type is
 * determined by the endpoint (/debit vs /credit).
 */
@Data
public class BalanceUpdateRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @Size(max = 100)
    private String reference;

    /**
     * Optional idempotency key. Allows the caller to safely retry a request
     * without applying the change twice. Implementation of true idempotency
     * is left for a future iteration; for now this field is logged for audit.
     */
    @Size(max = 64)
    private String idempotencyKey;
}
