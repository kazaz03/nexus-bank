package com.nexusbank.userservice.service;

import com.nexusbank.userservice.dto.request.LoginRequest;
import com.nexusbank.userservice.dto.response.LoginResponse;
import com.nexusbank.userservice.exception.ResourceNotFoundException;
import com.nexusbank.userservice.model.User;
import com.nexusbank.userservice.repository.UserRepository;
import com.nexusbank.userservice.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration}")
    private long expiration;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password"));

        if (!user.getIsActive()) {
            throw new IllegalArgumentException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user);
        return new LoginResponse(token, user.getId(), user.getEmail(), user.getRole().name(), expiration);
    }
}
