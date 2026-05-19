package com.nexusbank.userservice.service;

import com.nexusbank.userservice.dto.request.LoginRequest;
import com.nexusbank.userservice.dto.response.LoginResponse;
import com.nexusbank.userservice.exception.ResourceNotFoundException;
import com.nexusbank.userservice.model.User;
import com.nexusbank.userservice.repository.UserRepository;
import com.nexusbank.userservice.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private TokenRevocationService tokenRevocationService;

    @InjectMocks
    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "expiration", 86400000L);

        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setEmail("teller@nexusbank.com");
        activeUser.setPasswordHash("hashed");
        activeUser.setFirstName("Ana");
        activeUser.setLastName("Kovacevic");
        activeUser.setRole(User.Role.TELLER);
        activeUser.setCreatedAt(LocalDateTime.now());
        activeUser.setIsActive(true);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_withValidCredentials_returnsTokenAndUserInfo() {
        when(userRepository.findByEmail("teller@nexusbank.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(activeUser)).thenReturn("signed.jwt.token");

        LoginResponse response = authService.login(loginRequest("teller@nexusbank.com", "pass"));

        assertThat(response.getToken()).isEqualTo("signed.jwt.token");
        assertThat(response.getEmail()).isEqualTo("teller@nexusbank.com");
        assertThat(response.getRole()).isEqualTo("TELLER");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getExpiresIn()).isEqualTo(86400000L);
    }

    @Test
    void login_withUnknownEmail_throwsResourceNotFoundException() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> authService.login(loginRequest("nobody@test.com", "pass")));
    }

    @Test
    void login_withWrongPassword_throwsResourceNotFoundException() {
        when(userRepository.findByEmail("teller@nexusbank.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> authService.login(loginRequest("teller@nexusbank.com", "wrong")));
    }

    @Test
    void login_withInactiveAccount_throwsIllegalArgumentException() {
        activeUser.setIsActive(false);
        when(userRepository.findByEmail("teller@nexusbank.com")).thenReturn(Optional.of(activeUser));

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(loginRequest("teller@nexusbank.com", "pass")));
        verifyNoInteractions(passwordEncoder);
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_delegatesToRevocationService() {
        authService.logout("some.valid.token");
        verify(tokenRevocationService).revokeToken("some.valid.token");
    }

    @Test
    void logout_withInvalidToken_doesNotPropagateException() {
        doThrow(new RuntimeException("bad token"))
                .when(tokenRevocationService).revokeToken(any());

        authService.logout("invalid.token");
        // No exception should escape
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }
}
