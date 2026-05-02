package com.nexusbank.transactionservice.service;

import com.nexusbank.transactionservice.client.AccountClient;
import com.nexusbank.transactionservice.client.dto.AccountView;
import com.nexusbank.transactionservice.client.dto.BalanceUpdateResult;
import com.nexusbank.transactionservice.dto.request.TransferRequest;
import com.nexusbank.transactionservice.dto.response.TransferResponse;
import com.nexusbank.transactionservice.exception.AccountServiceException;
import com.nexusbank.transactionservice.model.ExchangeRate;
import com.nexusbank.transactionservice.model.Transaction;
import com.nexusbank.transactionservice.repository.ExchangeRateRepository;
import com.nexusbank.transactionservice.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Responsibilities:
 * <ol>
 *   <li>Validate input shape (delegated to bean validation on the request).</li>
 *   <li>Fetch and validate both accounts via {@link AccountClient}.</li>
 *   <li>Resolve an exchange rate from the local ExchangeRate table when
 *       the source and target accounts are denominated in different
 *       currencies.</li>
 *   <li>Apply atomic debit on the source account, then atomic credit on
 *       the target account, both via Account Service's internal API.</li>
 *   <li>If the credit leg fails after a successful debit, run a
 *       compensating credit ("refund") on the source account so the
 *       transfer is never half-applied.</li>
 *   <li>Persist the matched DEBIT/CREDIT transaction pair locally only
 *       once both legs have settled.</li>
 * </ol>
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountClient accountClient;
    private final TransactionRepository transactionRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public TransferService(AccountClient accountClient,
                           TransactionRepository transactionRepository,
                           ExchangeRateRepository exchangeRateRepository) {
        this.accountClient = accountClient;
        this.transactionRepository = transactionRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    public TransferResponse transfer(TransferRequest request) {
        if (request.getSourceIban().equalsIgnoreCase(request.getTargetIban())) {
            throw new IllegalArgumentException("Source and target IBAN must differ");
        }
        String reference = buildReference(request.getReference());

        // ── Phase 1: read-and-validate both accounts ─────────────────────
        AccountView source = accountClient.getByIban(request.getSourceIban(), false);
        AccountView target = accountClient.getByIban(request.getTargetIban(), false);
        validateAccountsForTransfer(source, target, request);

        // ── Phase 2: resolve exchange rate when currencies differ ────────
        BigDecimal targetAmount = request.getAmount();
        ExchangeRate appliedRate = null;
        if (!source.getCurrency().equalsIgnoreCase(target.getCurrency())) {
            appliedRate = resolveExchangeRate(source.getCurrency(), target.getCurrency());
            targetAmount = request.getAmount()
                    .multiply(appliedRate.getRate())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // ── Phase 3: debit the source ────────────────────────────────────
        BalanceUpdateResult debitResult;
        try {
            debitResult = accountClient.debit(
                    source.getId(), request.getAmount(), reference, reference + "-debit");
        } catch (AccountServiceException ex) {
            log.warn("Transfer aborted before debit: {}", ex.getMessage());
            throw ex;
        }

        // ── Phase 4: credit the target — compensate on failure ───────────
        BalanceUpdateResult creditResult;
        try {
            creditResult = accountClient.credit(
                    target.getId(), targetAmount, reference, reference + "-credit");
        } catch (AccountServiceException ex) {
            log.error("Credit leg failed, attempting compensation: {}", ex.getMessage());
            compensateDebit(source.getId(), request.getAmount(), reference);
            throw new AccountServiceException(
                    "Transfer failed during credit leg, source account refunded",
                    ex.getCause(), ex.isRetryable(),
                    ex.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // ── Phase 5: persist matched DEBIT + CREDIT records locally ──────
        return persistTransferRecords(
                request, source, target, appliedRate, targetAmount,
                debitResult, creditResult, reference);
    }

    private void validateAccountsForTransfer(AccountView source, AccountView target, TransferRequest request) {
        if (!"ACTIVE".equalsIgnoreCase(source.getStatus())) {
            throw new IllegalArgumentException("Source account is not active (status=" + source.getStatus() + ")");
        }
        if (!"ACTIVE".equalsIgnoreCase(target.getStatus())) {
            throw new IllegalArgumentException("Target account is not active (status=" + target.getStatus() + ")");
        }
        if (!request.getInitiatedBy().equals(source.getCustomerId())) {
            // Ownership check — proxies what would be JWT subject validation.
            throw new IllegalArgumentException("Initiator does not own the source account");
        }
        BigDecimal overdraft = source.getOverdraftLimit() != null ? source.getOverdraftLimit() : BigDecimal.ZERO;
        BigDecimal availableFunds = source.getBalance().add(overdraft);
        if (availableFunds.compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient funds on source account (available=" + availableFunds + ", requested=" + request.getAmount() + ")");
        }
        if ("SAVINGS".equalsIgnoreCase(target.getAccountType())
                && !target.getCustomerId().equals(source.getCustomerId())) {
            // F11 business rule: transfers to a savings account are limited to the customer's own accounts.
            throw new IllegalArgumentException("Transfers to a SAVINGS account are restricted to the same customer");
        }
    }

    private ExchangeRate resolveExchangeRate(String fromCurrency, String toCurrency) {
        LocalDate today = LocalDate.now();
        List<ExchangeRate> matches = exchangeRateRepository
                .findByFromCurrencyAndToCurrencyAndValidFromLessThanEqualAndValidToGreaterThanEqual(
                        fromCurrency.toUpperCase(),
                        toCurrency.toUpperCase(),
                        today,
                        today);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No active exchange rate found for " + fromCurrency + "→" + toCurrency);
        }
        return matches.get(0);
    }

    private void compensateDebit(Long sourceAccountId, BigDecimal amount, String reference) {
        try {
            accountClient.credit(sourceAccountId, amount, "REFUND-" + reference, reference + "-refund");
            log.info("Compensation credit applied for failed transfer ref={}", reference);
        } catch (AccountServiceException ex) {
            log.error("CRITICAL: compensation credit failed for ref={}, amount={} on source account {}. " +
                    "Manual reconciliation required.", reference, amount, sourceAccountId, ex);
        }
    }

    @Transactional
    public TransferResponse persistTransferRecords(TransferRequest request,
                                                   AccountView source,
                                                   AccountView target,
                                                   ExchangeRate appliedRate,
                                                   BigDecimal targetAmount,
                                                   BalanceUpdateResult debitResult,
                                                   BalanceUpdateResult creditResult,
                                                   String reference) {
        LocalDateTime now = LocalDateTime.now();

        Transaction debit = new Transaction();
        debit.setAccountId(source.getId());
        debit.setType(Transaction.TransactionType.TRANSFER);
        debit.setAmount(request.getAmount());
        debit.setCurrency(source.getCurrency());
        debit.setBalanceAfter(debitResult.getNewBalance());
        debit.setCounterpartyIban(target.getIban());
        debit.setReference(reference);
        debit.setExchangeRate(appliedRate);
        debit.setCreatedAt(now);
        debit.setCreatedBy(request.getInitiatedBy());
        debit.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(debit);

        Transaction credit = new Transaction();
        credit.setAccountId(target.getId());
        credit.setType(Transaction.TransactionType.TRANSFER);
        credit.setAmount(targetAmount);
        credit.setCurrency(target.getCurrency());
        credit.setBalanceAfter(creditResult.getNewBalance());
        credit.setCounterpartyIban(source.getIban());
        credit.setReference(reference);
        credit.setExchangeRate(appliedRate);
        credit.setCreatedAt(now);
        credit.setCreatedBy(request.getInitiatedBy());
        credit.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(credit);

        TransferResponse response = new TransferResponse();
        response.setReference(reference);
        response.setDebitTransactionId(debit.getId());
        response.setCreditTransactionId(credit.getId());
        response.setSourceIban(source.getIban());
        response.setTargetIban(target.getIban());
        response.setSourceAmount(request.getAmount());
        response.setSourceCurrency(source.getCurrency());
        response.setTargetAmount(targetAmount);
        response.setTargetCurrency(target.getCurrency());
        response.setExchangeRate(appliedRate != null ? appliedRate.getRate() : null);
        response.setSourceBalanceAfter(debitResult.getNewBalance());
        response.setTargetBalanceAfter(creditResult.getNewBalance());
        response.setExecutedAt(now);
        response.setStatus("COMPLETED");
        return response;
    }

    private String buildReference(String userSupplied) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if (userSupplied == null || userSupplied.isBlank()) {
            return "TRX-" + suffix;
        }
        return "TRX-" + suffix + "-" + userSupplied.replaceAll("\\s+", "_");
    }
}
