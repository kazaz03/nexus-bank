package com.nexusbank.accountservice.service;

import com.nexusbank.accountservice.dto.request.IssueDebitCardRequest;
import com.nexusbank.accountservice.dto.response.DebitCardResponse;
import com.nexusbank.accountservice.exception.AccountOperationException;
import com.nexusbank.accountservice.exception.ResourceNotFoundException;
import com.nexusbank.accountservice.model.Account;
import com.nexusbank.accountservice.model.DebitCard;
import com.nexusbank.accountservice.repository.AccountRepository;
import com.nexusbank.accountservice.repository.DebitCardRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class DebitCardService {

    private final DebitCardRepository debitCardRepository;
    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;

    public DebitCardService(DebitCardRepository debitCardRepository,
                            AccountRepository accountRepository,
                            ModelMapper modelMapper) {
        this.debitCardRepository = debitCardRepository;
        this.accountRepository = accountRepository;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public DebitCardResponse issueCard(Long accountId, IssueDebitCardRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AccountOperationException("Cannot issue card for inactive account");
        }
        if (account.getAccountType() == Account.AccountType.SAVINGS) {
            throw new AccountOperationException("Cannot issue debit card for savings account");
        }

        boolean hasActiveCard = debitCardRepository.findByAccountId(accountId).stream()
                .anyMatch(c -> c.getStatus() == DebitCard.CardStatus.ACTIVE
                        || c.getStatus() == DebitCard.CardStatus.PENDING);
        if (hasActiveCard) {
            throw new AccountOperationException("Account already has an active or pending debit card");
        }

        String lastFour = String.format("%04d", new Random().nextInt(10000));
        DebitCard card = new DebitCard();
        card.setAccount(account);
        card.setMaskedCardNumber("**** **** **** " + lastFour);
        card.setExpiryDate(LocalDate.now().plusYears(3));
        card.setCvvReference(UUID.randomUUID().toString().substring(0, 8));
        card.setStatus(DebitCard.CardStatus.PENDING);
        card.setIssuedAt(LocalDateTime.now());
        card.setIssuedBy(request.getIssuedBy());

        debitCardRepository.save(card);
        return toResponse(card);
    }

    public List<DebitCardResponse> getCardsByAccount(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account not found: " + accountId);
        }
        return debitCardRepository.findByAccountId(accountId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DebitCardResponse activateCard(Long cardId) {
        DebitCard card = findCard(cardId);
        if (card.getStatus() != DebitCard.CardStatus.PENDING) {
            throw new AccountOperationException("Only PENDING cards can be activated");
        }
        card.setStatus(DebitCard.CardStatus.ACTIVE);
        debitCardRepository.save(card);
        return toResponse(card);
    }

    @Transactional
    public DebitCardResponse blockCard(Long cardId) {
        DebitCard card = findCard(cardId);
        if (card.getStatus() == DebitCard.CardStatus.BLOCKED) {
            throw new AccountOperationException("Card is already blocked");
        }
        if (card.getStatus() == DebitCard.CardStatus.CANCELLED) {
            throw new AccountOperationException("Cannot block a cancelled card");
        }
        card.setStatus(DebitCard.CardStatus.BLOCKED);
        debitCardRepository.save(card);
        return toResponse(card);
    }

    @Transactional
    public DebitCardResponse unblockCard(Long cardId) {
        DebitCard card = findCard(cardId);
        if (card.getStatus() != DebitCard.CardStatus.BLOCKED) {
            throw new AccountOperationException("Only BLOCKED cards can be unblocked");
        }
        card.setStatus(DebitCard.CardStatus.ACTIVE);
        debitCardRepository.save(card);
        return toResponse(card);
    }

    private DebitCard findCard(Long cardId) {
        return debitCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit card not found: " + cardId));
    }

    private DebitCardResponse toResponse(DebitCard card) {
        DebitCardResponse response = new DebitCardResponse();
        response.setId(card.getId());
        response.setAccountId(card.getAccount().getId());
        response.setMaskedCardNumber(card.getMaskedCardNumber());
        response.setExpiryDate(card.getExpiryDate());
        response.setStatus(card.getStatus().name());
        response.setIssuedAt(card.getIssuedAt());
        response.setIssuedBy(card.getIssuedBy());
        return response;
    }
}
