package com.nexusbank.transactionservice.service;

import com.nexusbank.transactionservice.client.AccountClient;
import com.nexusbank.transactionservice.client.dto.AccountView;
import com.nexusbank.transactionservice.client.dto.BalanceUpdateResult;
import com.nexusbank.transactionservice.dto.request.CashTransactionRequest;
import com.nexusbank.transactionservice.dto.response.TransactionResponse;
import com.nexusbank.transactionservice.model.Transaction;
import com.nexusbank.transactionservice.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Counter (teller) cash operations — F10.
 *
 * Mirrors the synchronous pattern used by {@link TransferService}: the
 * balance change is applied through Account Service's internal API first,
 * and the local Transaction record is only persisted once the balance
 * change has settled. A DEPOSIT credits the account; a WITHDRAWAL debits it.
 */
@Service
public class CashService {

    private static final Logger log = LoggerFactory.getLogger(CashService.class);

    private final AccountClient accountClient;
    private final TransactionRepository transactionRepository;

    public CashService(AccountClient accountClient,
                       TransactionRepository transactionRepository) {
        this.accountClient = accountClient;
        this.transactionRepository = transactionRepository;
    }

    public TransactionResponse deposit(CashTransactionRequest request) {
        AccountView account = loadActiveAccount(request.getAccountId());
        String reference = buildReference("DEP", request.getReference());

        BalanceUpdateResult result = accountClient.credit(
                account.getId(), request.getAmount(), reference, reference);

        return persist(Transaction.TransactionType.DEPOSIT, account, request, result, reference);
    }

    public TransactionResponse withdraw(CashTransactionRequest request) {
        AccountView account = loadActiveAccount(request.getAccountId());
        String reference = buildReference("WDR", request.getReference());

        // Account Service atomically rejects (HTTP 422) when funds + overdraft
        // are insufficient, so no separate balance pre-check is required here.
        BalanceUpdateResult result = accountClient.debit(
                account.getId(), request.getAmount(), reference, reference);

        return persist(Transaction.TransactionType.WITHDRAWAL, account, request, result, reference);
    }

    private AccountView loadActiveAccount(Long accountId) {
        AccountView account = accountClient.getById(accountId, false);
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new IllegalArgumentException(
                    "Account is not active (status=" + account.getStatus() + ")");
        }
        return account;
    }

    @Transactional
    public TransactionResponse persist(Transaction.TransactionType type,
                                       AccountView account,
                                       CashTransactionRequest request,
                                       BalanceUpdateResult result,
                                       String reference) {
        LocalDateTime now = LocalDateTime.now();

        Transaction tx = new Transaction();
        tx.setAccountId(account.getId());
        tx.setType(type);
        tx.setAmount(request.getAmount());
        tx.setCurrency(account.getCurrency());
        tx.setBalanceAfter(result.getNewBalance());
        tx.setCounterpartyIban(null);
        tx.setReference(reference);
        tx.setCreatedAt(now);
        tx.setCreatedBy(request.getPerformedBy());
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(tx);

        log.info("Cash {} applied: accountId={}, amount={}, ref={}, newBalance={}",
                type, account.getId(), request.getAmount(), reference, result.getNewBalance());

        TransactionResponse response = new TransactionResponse();
        response.setId(tx.getId());
        response.setAccountId(tx.getAccountId());
        response.setType(tx.getType().name());
        response.setAmount(tx.getAmount());
        response.setCurrency(tx.getCurrency());
        response.setBalanceAfter(tx.getBalanceAfter());
        response.setCounterpartyIban(tx.getCounterpartyIban());
        response.setReference(tx.getReference());
        response.setCreatedAt(tx.getCreatedAt());
        response.setCreatedBy(tx.getCreatedBy());
        response.setStatus(tx.getStatus().name());
        return response;
    }

    private String buildReference(String prefix, String userSupplied) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if (userSupplied == null || userSupplied.isBlank()) {
            return prefix + "-" + suffix;
        }
        return prefix + "-" + suffix + "-" + userSupplied.replaceAll("\\s+", "_");
    }
}
