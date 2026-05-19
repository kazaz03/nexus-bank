package com.nexusbank.userservice.security;

import com.nexusbank.userservice.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "privateKey", (RSAPrivateKey) keyPair.getPrivate());
        ReflectionTestUtils.setField(jwtUtil, "publicKey",  (RSAPublicKey)  keyPair.getPublic());
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86_400_000L);

        user = new User();
        user.setId(7L);
        user.setEmail("teller@nexusbank.com");
        user.setRole(User.Role.TELLER);
    }

    // ── generateToken ─────────────────────────────────────────────────────────

    @Test
    void generateToken_returnsNonBlankToken() {
        assertThat(jwtUtil.generateToken(user)).isNotBlank();
    }

    @Test
    void generateToken_embeds_email_role_userId_jti() {
        String token = jwtUtil.generateToken(user);

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("teller@nexusbank.com");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("TELLER");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(7L);
        assertThat(jwtUtil.extractJti(token)).isNotBlank();
    }

    @Test
    void generateToken_eachCallProducesUniqueJti() {
        String jti1 = jwtUtil.extractJti(jwtUtil.generateToken(user));
        String jti2 = jwtUtil.extractJti(jwtUtil.generateToken(user));
        assertThat(jti1).isNotEqualTo(jti2);
    }

    // ── isTokenValid ──────────────────────────────────────────────────────────

    @Test
    void isTokenValid_withFreshToken_returnsTrue() {
        assertThat(jwtUtil.isTokenValid(jwtUtil.generateToken(user))).isTrue();
    }

    @Test
    void isTokenValid_withGarbageString_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("not.a.real.token")).isFalse();
    }

    @Test
    void isTokenValid_withEmptyString_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }
}
