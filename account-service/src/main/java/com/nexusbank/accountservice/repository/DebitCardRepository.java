package com.nexusbank.accountservice.repository;

import com.nexusbank.accountservice.model.DebitCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DebitCardRepository extends JpaRepository<DebitCard, Long> {
    List<DebitCard> findByAccountId(Long accountId);
}