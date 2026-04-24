package com.nexusbank.accountservice.service;

import com.nexusbank.accountservice.dto.request.IssueDebitCardRequest;
import com.nexusbank.accountservice.dto.response.DebitCardResponse;
import com.nexusbank.accountservice.exception.AccountOperationException;
import com.nexusbank.accountservice.exception.ResourceNotFoundException;
import com.nexusbank.accountservice.model.Account;
import com.nexusbank.accountservice.model.DebitCard;
import com.nexusbank.accountservice.repository.AccountRepository;
import com.nexusbank.accountservice.repository.DebitCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebitCardServiceTest {

    @Mock
    private DebitCardRepository debitCardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private DebitCardService debitCardService;

    private Account activeCheckingAccount;
    private DebitCard sampleCard;

    @BeforeEach
    void setUp() {
        activeCheckingAccount = new Account();
        activeCheckingAccount.setId(1L);
        activeCheckingAccount.setAccountType(Account.AccountType.CHECKING);
        activeCheckingAccount.setStatus(Account.AccountStatus.ACTIVE);
        activeCheckingAccount.setBalance(BigDecimal.ZERO);

        sampleCard = new DebitCard();
        sampleCard.setId(10L);
        sampleCard.setAccount(activeCheckingAccount);
        sampleCard.setMaskedCardNumber("**** **** **** 1234");
        sampleCard.setExpiryDate(LocalDate.now().plusYears(3));
        sampleCard.setStatus(DebitCard.CardStatus.PENDING);
        sampleCard.setIssuedAt(LocalDateTime.now());
    }

    // ── issueCard ─────────────────────────────────────────────────────────────

    @Test
    void issueCard_withActiveCheckingAccountAndNoExistingCard_succeeds() {
        IssueDebitCardRequest request = new IssueDebitCardRequest();
        request.setIssuedBy(99L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(activeCheckingAccount));
        when(debitCardRepository.findByAccountId(1L)).thenReturn(List.of());
        when(debitCardRepository.save(any(DebitCard.class))).thenReturn(sampleCard);

        DebitCardResponse response = debitCardService.issueCard(1L, request);

        assertThat(response).isNotNull();
        verify(debitCardRepository).save(any(DebitCard.class));
    }

    @Test
    void issueCard_forNonExistentAccount_throwsResourceNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> debitCardService.issueCard(99L, new IssueDebitCardRequest()));
    }

    @Test
    void issueCard_forInactiveAccount_throwsAccountOperationException() {
        activeCheckingAccount.setStatus(Account.AccountStatus.FROZEN);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(activeCheckingAccount));

        assertThrows(AccountOperationException.class,
                () -> debitCardService.issueCard(1L, new IssueDebitCardRequest()));
    }

    @Test
    void issueCard_forSavingsAccount_throwsAccountOperationException() {
        activeCheckingAccount.setAccountType(Account.AccountType.SAVINGS);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(activeCheckingAccount));

        assertThrows(AccountOperationException.class,
                () -> debitCardService.issueCard(1L, new IssueDebitCardRequest()));
    }

    @Test
    void issueCard_whenActiveCardAlreadyExists_throwsAccountOperationException() {
        DebitCard existingActiveCard = new DebitCard();
        existingActiveCard.setStatus(DebitCard.CardStatus.ACTIVE);
        existingActiveCard.setAccount(activeCheckingAccount);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(activeCheckingAccount));
        when(debitCardRepository.findByAccountId(1L)).thenReturn(List.of(existingActiveCard));

        assertThrows(AccountOperationException.class,
                () -> debitCardService.issueCard(1L, new IssueDebitCardRequest()));
    }

    // ── activateCard ─────────────────────────────────────────────────────────

    @Test
    void activateCard_whenPending_changesStatusToActive() {
        when(debitCardRepository.findById(10L)).thenReturn(Optional.of(sampleCard));
        when(debitCardRepository.save(any())).thenReturn(sampleCard);

        debitCardService.activateCard(10L);

        verify(debitCardRepository).save(argThat(c -> c.getStatus() == DebitCard.CardStatus.ACTIVE));
    }

    @Test
    void activateCard_whenNotPending_throwsAccountOperationException() {
        sampleCard.setStatus(DebitCard.CardStatus.ACTIVE);
        when(debitCardRepository.findById(10L)).thenReturn(Optional.of(sampleCard));

        assertThrows(AccountOperationException.class, () -> debitCardService.activateCard(10L));
    }

    // ── blockCard ─────────────────────────────────────────────────────────────

    @Test
    void blockCard_whenActive_changesStatusToBlocked() {
        sampleCard.setStatus(DebitCard.CardStatus.ACTIVE);
        when(debitCardRepository.findById(10L)).thenReturn(Optional.of(sampleCard));
        when(debitCardRepository.save(any())).thenReturn(sampleCard);

        debitCardService.blockCard(10L);

        verify(debitCardRepository).save(argThat(c -> c.getStatus() == DebitCard.CardStatus.BLOCKED));
    }

    @Test
    void blockCard_whenAlreadyBlocked_throwsAccountOperationException() {
        sampleCard.setStatus(DebitCard.CardStatus.BLOCKED);
        when(debitCardRepository.findById(10L)).thenReturn(Optional.of(sampleCard));

        assertThrows(AccountOperationException.class, () -> debitCardService.blockCard(10L));
    }

    @Test
    void blockCard_whenCancelled_throwsAccountOperationException() {
        sampleCard.setStatus(DebitCard.CardStatus.CANCELLED);
        when(debitCardRepository.findById(10L)).thenReturn(Optional.of(sampleCard));

        assertThrows(AccountOperationException.class, () -> debitCardService.blockCard(10L));
    }

    // ── unblockCard ───────────────────────────────────────────────────────────

    @Test
    void unblockCard_whenBlocked_changesStatusToActive() {
        sampleCard.setStatus(DebitCard.CardStatus.BLOCKED);
        when(debitCardRepository.findById(10L)).thenReturn(Optional.of(sampleCard));
        when(debitCardRepository.save(any())).thenReturn(sampleCard);

        debitCardService.unblockCard(10L);

        verify(debitCardRepository).save(argThat(c -> c.getStatus() == DebitCard.CardStatus.ACTIVE));
    }

    @Test
    void unblockCard_whenNotBlocked_throwsAccountOperationException() {
        sampleCard.setStatus(DebitCard.CardStatus.ACTIVE);
        when(debitCardRepository.findById(10L)).thenReturn(Optional.of(sampleCard));

        assertThrows(AccountOperationException.class, () -> debitCardService.unblockCard(10L));
    }

    // ── getCardsByAccount ─────────────────────────────────────────────────────

    @Test
    void getCardsByAccount_whenAccountExists_returnsCards() {
        when(accountRepository.existsById(1L)).thenReturn(true);
        when(debitCardRepository.findByAccountId(1L)).thenReturn(List.of(sampleCard));

        List<DebitCardResponse> result = debitCardService.getCardsByAccount(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getCardsByAccount_whenAccountNotFound_throwsResourceNotFoundException() {
        when(accountRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> debitCardService.getCardsByAccount(99L));
    }
}
