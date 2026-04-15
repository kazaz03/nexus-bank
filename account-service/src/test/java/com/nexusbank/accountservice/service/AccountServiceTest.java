package com.nexusbank.accountservice.service;

import com.nexusbank.accountservice.dto.request.CreateAccountRequest;
import com.nexusbank.accountservice.dto.response.AccountResponse;
import com.nexusbank.accountservice.dto.response.BalanceResponse;
import com.nexusbank.accountservice.exception.AccountOperationException;
import com.nexusbank.accountservice.exception.ResourceNotFoundException;
import com.nexusbank.accountservice.model.Account;
import com.nexusbank.accountservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private AccountService accountService;

    private Account sampleAccount;
    private AccountResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleAccount = new Account();
        sampleAccount.setId(1L);
        sampleAccount.setCustomerId(10L);
        sampleAccount.setIban("BA391234567890123456");
        sampleAccount.setAccountType(Account.AccountType.CHECKING);
        sampleAccount.setCurrency("BAM");
        sampleAccount.setBalance(BigDecimal.valueOf(500));
        sampleAccount.setOverdraftLimit(BigDecimal.ZERO);
        sampleAccount.setStatus(Account.AccountStatus.ACTIVE);
        sampleAccount.setCreatedAt(LocalDateTime.now());

        sampleResponse = new AccountResponse();
        sampleResponse.setId(1L);
        sampleResponse.setCustomerId(10L);
        sampleResponse.setIban("BA391234567890123456");
        sampleResponse.setAccountType("CHECKING");
        sampleResponse.setCurrency("BAM");
        sampleResponse.setBalance(BigDecimal.valueOf(500));
        sampleResponse.setStatus("ACTIVE");
    }

    // ── getAccount ──────────────────────────────────────────────────────────

    @Test
    void getAccount_whenFound_returnsResponse() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(sampleAccount));
        when(modelMapper.map(sampleAccount, AccountResponse.class)).thenReturn(sampleResponse);

        AccountResponse result = accountService.getAccount(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getIban()).isEqualTo("BA391234567890123456");
        verify(accountRepository).findById(1L);
    }

    @Test
    void getAccount_whenNotFound_throwsResourceNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.getAccount(99L));
    }

    // ── getAccountsByCustomerId ──────────────────────────────────────────────

    @Test
    void getAccountsByCustomerId_returnsList() {
        when(accountRepository.findByCustomerId(10L)).thenReturn(List.of(sampleAccount));
        when(modelMapper.map(sampleAccount, AccountResponse.class)).thenReturn(sampleResponse);

        List<AccountResponse> result = accountService.getAccountsByCustomerId(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerId()).isEqualTo(10L);
    }

    // ── getBalance ───────────────────────────────────────────────────────────

    @Test
    void getBalance_whenFound_returnsBalanceWithAvailable() {
        sampleAccount.setOverdraftLimit(BigDecimal.valueOf(200));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(sampleAccount));

        BalanceResponse result = accountService.getBalance(1L);

        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(result.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(700));
    }

    // ── createAccount ────────────────────────────────────────────────────────

    @Test
    void createAccount_withValidRequest_savesAndReturnsResponse() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCustomerId(10L);
        request.setAccountType("CHECKING");
        request.setCurrency("BAM");

        when(accountRepository.findByIban(any())).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(sampleAccount);
        when(modelMapper.map(any(Account.class), eq(AccountResponse.class))).thenReturn(sampleResponse);

        AccountResponse result = accountService.createAccount(request);

        assertThat(result).isNotNull();
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_withInvalidAccountType_throwsIllegalArgumentException() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCustomerId(10L);
        request.setAccountType("INVALID_TYPE");

        assertThrows(IllegalArgumentException.class, () -> accountService.createAccount(request));
        verify(accountRepository, never()).save(any());
    }

    // ── closeAccount ─────────────────────────────────────────────────────────

    @Test
    void closeAccount_withZeroBalance_closesSuccessfully() {
        sampleAccount.setBalance(BigDecimal.ZERO);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(sampleAccount));
        when(accountRepository.save(any())).thenReturn(sampleAccount);
        when(modelMapper.map(any(Account.class), eq(AccountResponse.class))).thenReturn(sampleResponse);

        accountService.closeAccount(1L, 99L);

        verify(accountRepository).save(argThat(a -> a.getStatus() == Account.AccountStatus.CLOSED));
    }

    @Test
    void closeAccount_withNonZeroBalance_throwsAccountOperationException() {
        sampleAccount.setBalance(BigDecimal.valueOf(100));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(sampleAccount));

        assertThrows(AccountOperationException.class, () -> accountService.closeAccount(1L, 99L));
    }

    @Test
    void closeAccount_whenAlreadyClosed_throwsAccountOperationException() {
        sampleAccount.setStatus(Account.AccountStatus.CLOSED);
        sampleAccount.setBalance(BigDecimal.ZERO);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(sampleAccount));

        assertThrows(AccountOperationException.class, () -> accountService.closeAccount(1L, 99L));
    }
}
