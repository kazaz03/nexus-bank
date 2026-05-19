package com.nexusbank.userservice.service;

import com.nexusbank.userservice.model.RevokedToken;
import com.nexusbank.userservice.repository.RevokedTokenRepository;
import com.nexusbank.userservice.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock private RevokedTokenRepository revokedTokenRepository;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private TokenRevocationService tokenRevocationService;

    // ── revokeToken ───────────────────────────────────────────────────────────

    @Test
    void revokeToken_savesEntryWithCorrectJtiAndUserId() {
        Claims claims = mockClaims("test-jti", 42L, new Date(System.currentTimeMillis() + 86_400_000));
        when(jwtUtil.extractAllClaims("token")).thenReturn(claims);
        when(revokedTokenRepository.existsByJti("test-jti")).thenReturn(false);

        tokenRevocationService.revokeToken("token");

        ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revokedTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getJti()).isEqualTo("test-jti");
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getRevokedAt()).isNotNull();
        assertThat(captor.getValue().getExpiresAt()).isNotNull();
    }

    @Test
    void revokeToken_whenAlreadyRevoked_doesNotSaveAgain() {
        Claims claims = mockClaims("test-jti", 1L, new Date(System.currentTimeMillis() + 86_400_000));
        when(jwtUtil.extractAllClaims("token")).thenReturn(claims);
        when(revokedTokenRepository.existsByJti("test-jti")).thenReturn(true);

        tokenRevocationService.revokeToken("token");

        verify(revokedTokenRepository, never()).save(any());
    }

    // ── isRevoked ─────────────────────────────────────────────────────────────

    @Test
    void isRevoked_whenJtiIsInDatabase_returnsTrue() {
        when(revokedTokenRepository.existsByJti("revoked-jti")).thenReturn(true);
        assertThat(tokenRevocationService.isRevoked("revoked-jti")).isTrue();
    }

    @Test
    void isRevoked_whenJtiIsNotInDatabase_returnsFalse() {
        when(revokedTokenRepository.existsByJti("active-jti")).thenReturn(false);
        assertThat(tokenRevocationService.isRevoked("active-jti")).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Claims mockClaims(String jti, Long userId, Date expiration) {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(jti);
        when(claims.get("userId", Long.class)).thenReturn(userId);
        when(claims.getExpiration()).thenReturn(expiration);
        return claims;
    }
}
