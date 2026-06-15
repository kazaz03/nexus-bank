package com.nexusbank.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal projection of a customer's KYC status, exposed over the internal
 * API so other services (e.g. account-service) can enforce KYC rules without
 * pulling the full customer record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusResponse {

    private Long customerId;
    private String kycStatus;
}
