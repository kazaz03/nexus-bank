package com.nexusbank.userservice.repository;

import com.nexusbank.userservice.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {
    boolean existsByJti(String jti);
    void deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
