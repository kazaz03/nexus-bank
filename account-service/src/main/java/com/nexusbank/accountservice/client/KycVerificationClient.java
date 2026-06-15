package com.nexusbank.accountservice.client;

import com.nexusbank.accountservice.client.dto.KycStatusView;
import com.nexusbank.accountservice.exception.AccountOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Synchronously verifies a customer's KYC status against User Service before
 * an account is opened (real service-level isolation for the KYC rule, not
 * just a UI guard).
 *
 * Enforcement can be toggled off via {@code nexus.kyc.enforcement.enabled}
 * (e.g. in integration tests where User Service is not available).
 */
@Component
public class KycVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(KycVerificationClient.class);
    private static final String USER_SERVICE_BASE_URL = "http://user-service";
    private static final String VERIFIED = "VERIFIED";

    private final RestTemplate loadBalancedRestTemplate;
    private final boolean enforcementEnabled;

    public KycVerificationClient(
            @Qualifier("loadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
            @Value("${nexus.kyc.enforcement.enabled:true}") boolean enforcementEnabled) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.enforcementEnabled = enforcementEnabled;
    }

    /**
     * Throws {@link AccountOperationException} (HTTP 422) unless the customer's
     * KYC status is VERIFIED. No-op when enforcement is disabled.
     */
    public void verifyEligibleForAccountOpening(Long customerId) {
        if (!enforcementEnabled) {
            return;
        }

        String status = fetchKycStatus(customerId);
        if (!VERIFIED.equalsIgnoreCase(status)) {
            throw new AccountOperationException(
                    "Customer KYC is not verified (status=" + status + "). "
                            + "An account can only be opened for a VERIFIED customer.");
        }
    }

    private String fetchKycStatus(Long customerId) {
        String url = USER_SERVICE_BASE_URL + "/api/internal/customers/" + customerId + "/kyc";
        try {
            KycStatusView view = loadBalancedRestTemplate.getForObject(url, KycStatusView.class);
            if (view == null || view.getKycStatus() == null) {
                throw new AccountOperationException(
                        "Unable to determine KYC status for customer " + customerId);
            }
            return view.getKycStatus();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new AccountOperationException("Customer not found for KYC verification: " + customerId);
        } catch (RestClientException ex) {
            log.warn("KYC verification call to User Service failed for customer {}: {}",
                    customerId, ex.getMessage());
            throw new AccountOperationException(
                    "KYC verification is temporarily unavailable. Please try again.");
        }
    }
}
