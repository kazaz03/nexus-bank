package com.nexusbank.accountservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO returned to other microservices (Transaction Service, Loan Service)
 * for inter-service synchronous communication. Exposes only the fields
 * needed by upstream services to validate business rules — not the full
 * Account entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountInternalResponse {

    private Long id;
    private Long customerId;
    private String iban;
    private String accountType;
    private String currency;
    private BigDecimal balance;
    private BigDecimal overdraftLimit;
    private String status;
}
