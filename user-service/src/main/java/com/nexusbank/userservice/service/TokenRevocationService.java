package com.nexusbank.userservice.service;

import com.nexusbank.userservice.model.RevokedToken;
import com.nexusbank.userservice.repository.RevokedTokenRepository;
import com.nexusbank.userservice.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtUtil jwtUtil;

    public TokenRevocationService(RevokedTokenRepository revokedTokenRepository, JwtUtil jwtUtil) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.jwtUtil = jwtUtil;
    }

    public void revokeToken(String token) {
        Claims claims = jwtUtil.extractAllClaims(token);
        String jti = claims.getId();

        if (revokedTokenRepository.existsByJti(jti)) {
            return;
        }

        RevokedToken revoked = new RevokedToken();
        revoked.setJti(jti);
        revoked.setUserId(claims.get("userId", Long.class));
        revoked.setRevokedAt(LocalDateTime.now());
        revoked.setExpiresAt(
                claims.getExpiration().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        );

        revokedTokenRepository.save(revoked);
    }

    public boolean isRevoked(String jti) {
        return revokedTokenRepository.existsByJti(jti);
    }
}
