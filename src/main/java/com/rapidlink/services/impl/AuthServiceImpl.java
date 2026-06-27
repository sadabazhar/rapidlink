package com.rapidlink.services.impl;

import com.rapidlink.dto.request.auth.LoginRequest;
import com.rapidlink.dto.request.auth.RegisterRequest;
import com.rapidlink.dto.response.auth.LoginResponse;
import com.rapidlink.dto.response.auth.RegisterResponse;
import com.rapidlink.entity.User;
import com.rapidlink.exception.EmailAlreadyExistsException;
import com.rapidlink.mapper.UserMapper;
import com.rapidlink.repository.UserRepository;
import com.rapidlink.security.JwtService;
import com.rapidlink.security.RapidLinkUserDetails;
import com.rapidlink.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
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
        User user = UserMapper.toEntity(request, passwordEncoder.encode(request.password()));

        // Persist the user and obtain generated fields such as ID and timestamps.
        User savedUser = userRepository.save(user);

        log.info("User registered successfully. userId={}, email={}",
                savedUser.getId(), savedUser.getEmail());

        // Return the registration result without exposing the password.
        return UserMapper.toRegisterResponse(savedUser);
    }

    /**
     * Authenticates a user and returns JWT tokens.
     *
     * Login flow:
     * - Authenticate the user's credentials
     * - Generate access and refresh tokens
     * - Return authentication details
     */
    @Override
    public LoginResponse login(LoginRequest request) {

        // Normalize the email
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        log.info("Login requested for email={}", email);

        // Authenticate the user's email and password.
        // Spring Security automatically verifies:
        // - Password correctness
        // - Account is enabled (isEnabled())
        // - Account is not locked (isAccountNonLocked())
        Authentication authentication =  authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        request.password()
                )
        );

        // The authenticated principal is our custom UserDetails implementation.
        RapidLinkUserDetails userDetails =
                (RapidLinkUserDetails) authentication.getPrincipal();

        // Retrieve the underlying User entity.
        User user = userDetails.getUser();

        // Generate JWT tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Access token lifetime (in seconds).
        long expiresIn = jwtService.getAccessTokenExpirationInSeconds();

        log.info("User logged in successfully. userId={}, email={}",
                user.getId(), user.getEmail());

        // Return authentication response
        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn
        );
    }
}
