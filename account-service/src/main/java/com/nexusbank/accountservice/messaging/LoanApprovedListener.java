package com.nexusbank.accountservice.messaging;

import com.nexusbank.accountservice.config.RabbitConfig;
import com.nexusbank.accountservice.dto.request.BalanceUpdateRequest;
import com.nexusbank.accountservice.dto.response.BalanceUpdateResponse;
import com.nexusbank.accountservice.messaging.event.DisbursementCompletedEvent;
import com.nexusbank.accountservice.messaging.event.DisbursementFailedEvent;
import com.nexusbank.accountservice.messaging.event.LoanApprovedEvent;
import com.nexusbank.accountservice.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Account-service half of the F14 saga.
 *
 * On loan.approved we try to credit the customer's account. The credit is the
 * second local transaction in the saga (the first being the loan status update
 * inside loan-service). Result is reported back through one of two events:
 *
 *   success → loan.disbursement.completed → loan-service marks loan DISBURSED
 *   failure → loan.disbursement.failed    → loan-service rolls loan to REJECTED
 */
@Component
public class LoanApprovedListener {

    private static final Logger log = LoggerFactory.getLogger(LoanApprovedListener.class);

    private final AccountService accountService;
    private final RabbitTemplate rabbitTemplate;

    public LoanApprovedListener(AccountService accountService, RabbitTemplate rabbitTemplate) {
        this.accountService = accountService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitConfig.LOAN_APPROVED_QUEUE)
    public void onLoanApproved(LoanApprovedEvent event) {
        log.info("Received loan.approved: loanId={}, accountId={}, amount={}",
                event.getLoanApplicationId(), event.getAccountId(), event.getAmountApproved());

        BalanceUpdateRequest creditRequest = new BalanceUpdateRequest();
        creditRequest.setAmount(event.getAmountApproved());
        creditRequest.setReference("LOAN-" + event.getLoanApplicationId());
        creditRequest.setIdempotencyKey("LOAN-DISBURSE-" + event.getLoanApplicationId());

        try {
            BalanceUpdateResponse result = accountService.credit(event.getAccountId(), creditRequest);
            DisbursementCompletedEvent completed = new DisbursementCompletedEvent(
                    event.getLoanApplicationId(),
                    event.getAccountId(),
                    event.getAmountApproved(),
                    result.getNewBalance());
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EXCHANGE,
                    RabbitConfig.DISBURSEMENT_COMPLETED_KEY,
                    completed);
            log.info("Disbursement completed for loanId={}, new balance={}",
                    event.getLoanApplicationId(), result.getNewBalance());
        } catch (Exception ex) {
            log.warn("Disbursement failed for loanId={}: {}", event.getLoanApplicationId(), ex.getMessage());
            DisbursementFailedEvent failed = new DisbursementFailedEvent(
                    event.getLoanApplicationId(),
                    event.getAccountId(),
                    ex.getMessage());
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EXCHANGE,
                    RabbitConfig.DISBURSEMENT_FAILED_KEY,
                    failed);
        }
    }
}
