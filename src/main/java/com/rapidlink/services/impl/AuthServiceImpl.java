package com.rapidlink.services.impl;

import com.rapidlink.dto.request.auth.LoginRequest;
import com.rapidlink.dto.request.auth.RegisterRequest;
import com.rapidlink.dto.response.auth.LoginResponse;
import com.rapidlink.dto.response.auth.RegisterResponse;
import com.rapidlink.entity.User;
import com.rapidlink.exception.EmailAlreadyExistsException;
import com.rapidlink.repository.UserRepository;
import com.rapidlink.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

/**
 * Handles authentication-related business logic such as
 * user registration, login, logout etc
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers a new user account.
     *
     * Registration flow:
     * - Normalize the email
     * - Check if the email is already registered
     * - Hash the password
     * - Save the user
     * - Return the registered user details
     */
    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // Normalize the email to avoid duplicate accounts caused by letter casing.
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        log.info("Registration requested for email={}", email);

        // Ensure the email is not already registered
        if (userRepository.existsByEmail(email)) {

            log.warn("Registration failed: email already exists. email={}", email);

            throw new EmailAlreadyExistsException(
                    "An account with this email already exists."
            );
        }

        // Create a new user with a securely hashed password.
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        // Persist the user and obtain generated fields such as ID and timestamps.
        User savedUser = userRepository.save(user);

        log.info("User registered successfully. userId={}, email={}",
                savedUser.getId(), savedUser.getEmail());

        // Return the registration result without exposing the password.
        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        throw new UnsupportedOperationException("Login is not implemented yet.");
    }
}
