package com.nexusbank.loanservice.messaging;

import com.nexusbank.loanservice.config.RabbitConfig;
import com.nexusbank.loanservice.messaging.event.LoanApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoanEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public LoanEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishLoanApproved(LoanApprovedEvent event) {
        log.info("Publishing loan.approved event: loanId={}, accountId={}, amount={}",
                event.getLoanApplicationId(), event.getAccountId(), event.getAmountApproved());
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.LOAN_APPROVED_KEY,
                event);
    }
}
