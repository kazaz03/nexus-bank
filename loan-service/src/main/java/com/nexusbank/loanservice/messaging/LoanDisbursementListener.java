package com.nexusbank.loanservice.messaging;

import com.nexusbank.loanservice.config.RabbitConfig;
import com.nexusbank.loanservice.messaging.event.DisbursementCompletedEvent;
import com.nexusbank.loanservice.messaging.event.DisbursementFailedEvent;
import com.nexusbank.loanservice.service.LoanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens for the two terminal events that complete the F14 saga and asks
 * LoanService to update the loan accordingly:
 *   - DisbursementCompletedEvent → mark loan as DISBURSED (final state)
 *   - DisbursementFailedEvent    → roll loan back to REJECTED (inverse action)
 */
@Component
public class LoanDisbursementListener {

    private static final Logger log = LoggerFactory.getLogger(LoanDisbursementListener.class);

    private final LoanService loanService;

    public LoanDisbursementListener(LoanService loanService) {
        this.loanService = loanService;
    }

    @RabbitListener(queues = RabbitConfig.DISBURSEMENT_COMPLETED_QUEUE)
    public void onDisbursementCompleted(DisbursementCompletedEvent event) {
        log.info("Received loan.disbursement.completed: loanId={}, accountId={}, amount={}",
                event.getLoanApplicationId(), event.getAccountId(), event.getAmountCredited());
        try {
            loanService.markAsDisbursed(event.getLoanApplicationId());
            log.info("Successfully marked loan {} as DISBURSED", event.getLoanApplicationId());
        } catch (Exception e) {
            log.error("Failed to mark loan {} as DISBURSED", event.getLoanApplicationId(), e);
            throw e;
        }
    }

    @RabbitListener(queues = RabbitConfig.DISBURSEMENT_FAILED_QUEUE)
    public void onDisbursementFailed(DisbursementFailedEvent event) {
        log.warn("Received loan.disbursement.failed: loanId={}, reason={}",
                event.getLoanApplicationId(), event.getReason());
        loanService.markAsRejectedAfterFailedDisbursement(event.getLoanApplicationId(), event.getReason());
    }
}
