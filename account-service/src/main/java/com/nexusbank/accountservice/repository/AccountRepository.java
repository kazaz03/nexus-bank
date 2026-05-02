package com.nexusbank.accountservice.repository;

import com.nexusbank.accountservice.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByCustomerId(Long customerId);

    Optional<Account> findByIban(String iban);
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance - :amount " +
           "WHERE a.id = :accountId " +
           "AND a.status = com.nexusbank.accountservice.model.Account.AccountStatus.ACTIVE " +
           "AND (a.balance + COALESCE(a.overdraftLimit, 0)) >= :amount")
    int debitIfSufficient(@Param("accountId") Long accountId,
                          @Param("amount") BigDecimal amount);
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance + :amount " +
           "WHERE a.id = :accountId " +
           "AND a.status = com.nexusbank.accountservice.model.Account.AccountStatus.ACTIVE")
    int creditIfActive(@Param("accountId") Long accountId,
                       @Param("amount") BigDecimal amount);
}
