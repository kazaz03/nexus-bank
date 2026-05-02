package com.nexusbank.accountservice.service;

import com.nexusbank.accountservice.dto.request.BalanceUpdateRequest;
import com.nexusbank.accountservice.dto.request.CreateAccountRequest;
import com.nexusbank.accountservice.dto.response.AccountInternalResponse;
import com.nexusbank.accountservice.dto.response.AccountResponse;
import com.nexusbank.accountservice.dto.response.BalanceResponse;
import com.nexusbank.accountservice.dto.response.BalanceUpdateResponse;
import com.nexusbank.accountservice.exception.AccountOperationException;
import com.nexusbank.accountservice.exception.ResourceNotFoundException;
import com.nexusbank.accountservice.model.Account;
import com.nexusbank.accountservice.repository.AccountRepository;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;

    public AccountService(AccountRepository accountRepository, ModelMapper modelMapper) {
        this.accountRepository = accountRepository;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        Account.AccountType accountType;
        try {
            accountType = Account.AccountType.valueOf(request.getAccountType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid account type: " + request.getAccountType());
        }

        Account account = new Account();
        account.setCustomerId(request.getCustomerId());
        account.setIban(generateIban());
        account.setAccountType(accountType);
        account.setCurrency(request.getCurrency() != null ? request.getCurrency().toUpperCase() : "BAM");
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        account.setCreatedBy(request.getCreatedBy());

        if (accountType == Account.AccountType.CHECKING && request.getOverdraftLimit() != null) {
            account.setOverdraftLimit(request.getOverdraftLimit());
        } else {
            account.setOverdraftLimit(BigDecimal.ZERO);
        }

        if (accountType == Account.AccountType.SAVINGS && request.getInterestRate() != null) {
            account.setInterestRate(request.getInterestRate());
        }

        accountRepository.save(account);
        return toResponse(account);
    }

    public AccountResponse getAccount(Long id) {
        Account account = findById(id);
        return toResponse(account);
    }

    public List<AccountResponse> getAccountsByCustomerId(Long customerId) {
        return accountRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public BalanceResponse getBalance(Long id) {
        Account account = findById(id);
        BigDecimal available = account.getBalance().add(
                account.getOverdraftLimit() != null ? account.getOverdraftLimit() : BigDecimal.ZERO
        );
        return new BalanceResponse(
                account.getId(),
                account.getIban(),
                account.getCurrency(),
                account.getBalance(),
                account.getOverdraftLimit(),
                available,
                account.getStatus().name()
        );
    }

    @Transactional
    public AccountResponse closeAccount(Long id, Long closedByUserId) {
        Account account = findById(id);

        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new AccountOperationException("Account is already closed");
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new AccountOperationException("Cannot close account with non-zero balance");
        }

        account.setStatus(Account.AccountStatus.CLOSED);
        account.setClosedAt(LocalDateTime.now());
        account.setClosedBy(closedByUserId);
        accountRepository.save(account);
        return toResponse(account);
    }

    //  Internal API used by other microservices via synchronous HTTP calls.
    //  These methods are exposed through AccountInternalController
    public AccountInternalResponse getInternalByIban(String iban) {
        Account account = accountRepository.findByIban(iban)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for IBAN: " + iban));
        return toInternalResponse(account);
    }

    public AccountInternalResponse getInternalById(Long id) {
        Account account = findById(id);
        return toInternalResponse(account);
    }

    /**
     * Atomically debits the given amount from the account. Returns the new
     * balance on success. Throws {@link AccountOperationException} (mapped to
     * HTTP 422) if the account is not active or has insufficient funds —
     * the caller (Transaction Service) is responsible for handling this
     * gracefully.
     */
    @Transactional
    public BalanceUpdateResponse debit(Long accountId, BalanceUpdateRequest request) {
        validateAmount(request.getAmount());

        int rowsUpdated = accountRepository.debitIfSufficient(accountId, request.getAmount());
        if (rowsUpdated != 1) {
            // Either account not found, not ACTIVE, or insufficient funds.
            // Distinguished by reading the row.
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
            if (account.getStatus() != Account.AccountStatus.ACTIVE) {
                throw new AccountOperationException(
                        "Account is not active (status=" + account.getStatus() + ")");
            }
            throw new AccountOperationException(
                    "Insufficient funds on account " + account.getIban() +
                            " (requested=" + request.getAmount() + ")");
        }

        // Re-read to obtain the new balance for the response.
        Account updated = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found after debit: " + accountId));
        log.info("Internal DEBIT applied: accountId={}, amount={}, ref={}, idempotencyKey={}",
                accountId, request.getAmount(), request.getReference(), request.getIdempotencyKey());
        return new BalanceUpdateResponse(
                updated.getId(),
                updated.getIban(),
                updated.getCurrency(),
                updated.getBalance());
    }

    /**
     * Atomically credits the given amount to the account. Returns the new
     * balance on success. Throws {@link AccountOperationException} if the
     * account is not active.
     */
    @Transactional
    public BalanceUpdateResponse credit(Long accountId, BalanceUpdateRequest request) {
        validateAmount(request.getAmount());

        int rowsUpdated = accountRepository.creditIfActive(accountId, request.getAmount());
        if (rowsUpdated != 1) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
            throw new AccountOperationException(
                    "Account is not active (status=" + account.getStatus() + ")");
        }

        Account updated = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found after credit: " + accountId));
        log.info("Internal CREDIT applied: accountId={}, amount={}, ref={}, idempotencyKey={}",
                accountId, request.getAmount(), request.getReference(), request.getIdempotencyKey());
        return new BalanceUpdateResponse(
                updated.getId(),
                updated.getIban(),
                updated.getCurrency(),
                updated.getBalance());
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private AccountInternalResponse toInternalResponse(Account account) {
        return new AccountInternalResponse(
                account.getId(),
                account.getCustomerId(),
                account.getIban(),
                account.getAccountType().name(),
                account.getCurrency(),
                account.getBalance(),
                account.getOverdraftLimit(),
                account.getStatus().name());
    }


    private Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    private String generateIban() {
        long randomPart = Math.abs(new Random().nextLong() % 1_000_000_000_000_000L);
        String iban = String.format("BA39%016d", randomPart);
        while (accountRepository.findByIban(iban).isPresent()) {
            randomPart = Math.abs(new Random().nextLong() % 1_000_000_000_000_000L);
            iban = String.format("BA39%016d", randomPart);
        }
        return iban;
    }

    private AccountResponse toResponse(Account account) {
        AccountResponse response = modelMapper.map(account, AccountResponse.class);
        response.setAccountType(account.getAccountType().name());
        response.setStatus(account.getStatus().name());
        return response;
    }
}
