package com.nexusbank.accountservice.repository;

import com.nexusbank.accountservice.model.DebitCard;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DebitCardRepository extends JpaRepository<DebitCard, Long> {

    @Override
    @EntityGraph(attributePaths = {"account"})
    Optional<DebitCard> findById(Long id);

    @EntityGraph(attributePaths = {"account"})
    List<DebitCard> findByAccountId(Long accountId);
}