package com.nexusbank.accountservice;

import com.nexusbank.accountservice.model.Account;
import com.nexusbank.accountservice.model.DebitCard;
import com.nexusbank.accountservice.repository.AccountRepository;
import com.nexusbank.accountservice.repository.DebitCardRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DataLoader implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final DebitCardRepository debitCardRepository;

    public DataLoader(AccountRepository accountRepository,
                      DebitCardRepository debitCardRepository) {
        this.accountRepository = accountRepository;
        this.debitCardRepository = debitCardRepository;
    }

    @Override
    public void run(String... args) {
        if (accountRepository.count() > 0) {
            return;
        }

        // Account 1 - Checking
        Account account1 = new Account();
        account1.setCustomerId(1L);
        account1.setIban("BA393000000123456789");
        account1.setAccountType(Account.AccountType.CHECKING);
        account1.setCurrency("BAM");
        account1.setBalance(new BigDecimal("3842.50"));
        account1.setOverdraftLimit(new BigDecimal("500.00"));
        account1.setStatus(Account.AccountStatus.ACTIVE);
        account1.setCreatedAt(LocalDateTime.now());
        account1.setCreatedBy(2L);
        accountRepository.save(account1);

        // Account 2 - Savings
        Account account2 = new Account();
        account2.setCustomerId(1L);
        account2.setIban("BA393000000987654321");
        account2.setAccountType(Account.AccountType.SAVINGS);
        account2.setCurrency("BAM");
        account2.setBalance(new BigDecimal("12100.00"));
        account2.setInterestRate(new BigDecimal("2.50"));
        account2.setStatus(Account.AccountStatus.ACTIVE);
        account2.setCreatedAt(LocalDateTime.now());
        account2.setCreatedBy(2L);
        accountRepository.save(account2);

        // Account 3 - Foreign
        Account account3 = new Account();
        account3.setCustomerId(2L);
        account3.setIban("BA393000000111222333");
        account3.setAccountType(Account.AccountType.FOREIGN);
        account3.setCurrency("EUR");
        account3.setBalance(new BigDecimal("5000.00"));
        account3.setStatus(Account.AccountStatus.ACTIVE);
        account3.setCreatedAt(LocalDateTime.now());
        account3.setCreatedBy(2L);
        accountRepository.save(account3);

        // DebitCard za account 1
        DebitCard card1 = new DebitCard();
        card1.setAccount(account1);
        card1.setMaskedCardNumber("**** **** **** 6789");
        card1.setExpiryDate(LocalDate.of(2028, 12, 31));
        card1.setCvvReference("hashed_cvv_1");
        card1.setStatus(DebitCard.CardStatus.ACTIVE);
        card1.setIssuedAt(LocalDateTime.now());
        card1.setIssuedBy(2L);
        debitCardRepository.save(card1);

        System.out.println("Account Service - data loaded");
    }
}