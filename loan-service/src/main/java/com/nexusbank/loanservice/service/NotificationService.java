package com.nexusbank.loanservice.service;

import com.nexusbank.loanservice.dto.response.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thin façade over {@link SseEmitterManager} that builds typed notification
 * events for the loan saga and dispatches them to the right user(s).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SseEmitterManager sseEmitterManager;

    public NotificationService(SseEmitterManager sseEmitterManager) {
        this.sseEmitterManager = sseEmitterManager;
    }

    /** Notifies the reviewing officer that a loan they approved has been disbursed. */
    public void notifyDisbursed(Long loanId, Long reviewerUserId, Long customerUserId) {
        NotificationEvent officerEvent = new NotificationEvent(
                "LOAN_DISBURSED",
                loanId,
                "Loan #" + loanId + " has been successfully disbursed."
        );
        NotificationEvent customerEvent = new NotificationEvent(
                "LOAN_DISBURSED",
                loanId,
                "Your loan #" + loanId + " has been approved and the funds have been disbursed to your account."
        );

        if (reviewerUserId != null) sseEmitterManager.send(reviewerUserId, officerEvent);
        if (customerUserId != null) sseEmitterManager.send(customerUserId, customerEvent);

        log.info("LOAN_DISBURSED notification sent: loanId={}, reviewer={}, customer={}",
                loanId, reviewerUserId, customerUserId);
    }

    /** Notifies both parties that disbursement failed and the loan was rolled back. */
    public void notifyDisbursementFailed(Long loanId, Long reviewerUserId, Long customerUserId, String reason) {
        NotificationEvent officerEvent = new NotificationEvent(
                "LOAN_REJECTED",
                loanId,
                "Loan #" + loanId + " disbursement failed and has been rolled back. Reason: " + reason
        );
        NotificationEvent customerEvent = new NotificationEvent(
                "LOAN_REJECTED",
                loanId,
                "Unfortunately, disbursement for your loan #" + loanId + " could not be completed."
        );

        if (reviewerUserId != null) sseEmitterManager.send(reviewerUserId, officerEvent);
        if (customerUserId != null) sseEmitterManager.send(customerUserId, customerEvent);

        log.info("LOAN_REJECTED notification sent: loanId={}, reviewer={}, customer={}",
                loanId, reviewerUserId, customerUserId);
    }
}
