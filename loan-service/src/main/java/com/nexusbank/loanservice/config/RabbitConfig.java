package com.nexusbank.loanservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;

/**
 * RabbitMQ topology for the loan disbursement saga (F14).
 *
 * Topology:
 *   exchange:  loan.exchange (topic)
 *     ├─► routing-key "loan.approved"                → loan.approved.queue          (consumed by account-service)
 *     ├─► routing-key "loan.disbursement.completed"  → loan.disbursement.completed.queue (consumed by loan-service)
 *     └─► routing-key "loan.disbursement.failed"     → loan.disbursement.failed.queue    (consumed by loan-service)
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "loan.exchange";

    public static final String LOAN_APPROVED_QUEUE = "loan.approved.queue";
    public static final String DISBURSEMENT_COMPLETED_QUEUE = "loan.disbursement.completed.queue";
    public static final String DISBURSEMENT_FAILED_QUEUE = "loan.disbursement.failed.queue";

    public static final String LOAN_APPROVED_KEY = "loan.approved";
    public static final String DISBURSEMENT_COMPLETED_KEY = "loan.disbursement.completed";
    public static final String DISBURSEMENT_FAILED_KEY = "loan.disbursement.failed";

    @Bean
    public TopicExchange loanExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue loanApprovedQueue() {
        return new Queue(LOAN_APPROVED_QUEUE, true);
    }

    @Bean
    public Queue disbursementCompletedQueue() {
        return new Queue(DISBURSEMENT_COMPLETED_QUEUE, true);
    }

    @Bean
    public Queue disbursementFailedQueue() {
        return new Queue(DISBURSEMENT_FAILED_QUEUE, true);
    }

    @Bean
    public Binding bindLoanApproved(Queue loanApprovedQueue, TopicExchange loanExchange) {
        return BindingBuilder.bind(loanApprovedQueue).to(loanExchange).with(LOAN_APPROVED_KEY);
    }

    @Bean
    public Binding bindDisbursementCompleted(Queue disbursementCompletedQueue, TopicExchange loanExchange) {
        return BindingBuilder.bind(disbursementCompletedQueue).to(loanExchange).with(DISBURSEMENT_COMPLETED_KEY);
    }

    @Bean
    public Binding bindDisbursementFailed(Queue disbursementFailedQueue, TopicExchange loanExchange) {
        return BindingBuilder.bind(disbursementFailedQueue).to(loanExchange).with(DISBURSEMENT_FAILED_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
