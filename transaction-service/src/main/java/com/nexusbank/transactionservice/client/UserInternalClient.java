package com.nexusbank.transactionservice.client;

import com.nexusbank.transactionservice.exception.AccountServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Synchronously resolves a customer profile ID (customers.id) to the owning
 * User's ID (users.id) via User Service's internal API.
 *
 * Called by TransferService to verify that the authenticated caller
 * (identified by X-User-Id / users.id from the JWT) actually owns the
 * source account (which stores customerId, not userId).
 */
@Component
public class UserInternalClient {

    private static final Logger log = LoggerFactory.getLogger(UserInternalClient.class);
    private static final String USER_SERVICE = "http://user-service";

    private final RestTemplate loadBalancedRestTemplate;

    public UserInternalClient(@Qualifier("loadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
    }

    /**
     * Returns the userId (users.id) that owns the given customerId.
     * Throws AccountServiceException (mapped to HTTP 422) on failure.
     */
    @SuppressWarnings("unchecked")
    public Long resolveUserId(Long customerId) {
        String url = USER_SERVICE + "/api/internal/customers/" + customerId + "/user-id";
        try {
            Map<String, Object> body = loadBalancedRestTemplate.getForObject(url, Map.class);
            if (body == null || body.get("userId") == null) {
                throw new AccountServiceException("Could not resolve userId for customerId=" + customerId,
                        null, false, HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return ((Number) body.get("userId")).longValue();
        } catch (HttpClientErrorException.NotFound e) {
            throw new AccountServiceException("Customer not found: " + customerId,
                    e, false, HttpStatus.UNPROCESSABLE_ENTITY);
        } catch (RestClientException e) {
            log.warn("UserService call failed while resolving customerId={}: {}", customerId, e.getMessage());
            throw new AccountServiceException("User service temporarily unavailable",
                    e, true, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
