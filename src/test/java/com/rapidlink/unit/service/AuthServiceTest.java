package com.rapidlink.unit.service;

import com.rapidlink.dto.request.auth.LoginRequest;
import com.rapidlink.dto.request.auth.RegisterRequest;
import com.rapidlink.dto.response.auth.LoginResponse;
import com.rapidlink.dto.response.auth.RegisterResponse;
import com.rapidlink.entity.User;
import com.rapidlink.enums.Role;
import com.rapidlink.exception.EmailAlreadyExistsException;
import com.rapidlink.repository.UserRepository;
import com.rapidlink.security.JwtService;
import com.rapidlink.security.RapidLinkUserDetails;
import com.rapidlink.services.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    // ------------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------------
    @Test
    void shouldRegisterUserSuccessfully() {

        RegisterRequest request = createRegisterRequest();

        when(userRepository.existsByEmail("sadab@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode(request.password()))
                .thenReturn("hashedPassword");

        User savedUser = createUser();

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        RegisterResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.firstName()).isEqualTo("Sadab");
        assertThat(response.lastName()).isEqualTo("Azhar");
        assertThat(response.email()).isEqualTo("sadab@example.com");

        verify(userRepository).existsByEmail("sadab@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldNormalizeEmailBeforeRegistration() {

        RegisterRequest request = new RegisterRequest(
                "Sadab",
                "Azhar",
                "  SADAB@Example.COM  ",
                "password123"
        );

        when(userRepository.existsByEmail("sadab@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode(anyString()))
                .thenReturn("hashedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        verify(userRepository)
                .existsByEmail("sadab@example.com");

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getEmail())
                .isEqualTo("sadab@example.com");
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {

        RegisterRequest request = createRegisterRequest();

        when(userRepository.existsByEmail("sadab@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() ->
                authService.register(request)
        )
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("An account with this email already exists.");

        verify(userRepository).existsByEmail("sadab@example.com");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldEncodePasswordBeforeSavingUser() {

        RegisterRequest request = createRegisterRequest();

        when(userRepository.existsByEmail(anyString()))
                .thenReturn(false);

        when(passwordEncoder.encode("password123"))
                .thenReturn("hashedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getPasswordHash())
                .isEqualTo("hashedPassword");

        assertThat(userCaptor.getValue().getPasswordHash())
                .isNotEqualTo(request.password());
    }

    // ------------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------------
    @Test
    void shouldLoginSuccessfully() {

        LoginRequest request = createLoginRequest();

        User user = createUser();

        mockSuccessfulAuthentication(user);

        mockJwtGeneration(user);

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);

        verify(authenticationManager).authenticate(any(Authentication.class));
        verify(jwtService).generateAccessToken(user);
        verify(jwtService).generateRefreshToken(user);
    }

    @Test
    void shouldNormalizeEmailBeforeAuthentication() {

        LoginRequest request = new LoginRequest(
                "  SADAB@Example.COM  ",
                "password123"
        );

        User user = createUser();

        mockSuccessfulAuthentication(user);

        mockJwtGeneration(user);

        authService.login(request);

        ArgumentCaptor<Authentication> captor =
                ArgumentCaptor.forClass(Authentication.class);

        verify(authenticationManager).authenticate(captor.capture());

        UsernamePasswordAuthenticationToken token =
                (UsernamePasswordAuthenticationToken) captor.getValue();

        assertThat(token.getPrincipal())
                .isEqualTo("sadab@example.com");

        assertThat(token.getCredentials())
                .isEqualTo("password123");
    }


    @Test
    void shouldGenerateAccessAndRefreshTokens() {

        LoginRequest request = createLoginRequest();

        User user = createUser();

        mockSuccessfulAuthentication(user);

        mockJwtGeneration(user);

        authService.login(request);

        verify(jwtService).generateAccessToken(user);
        verify(jwtService).generateRefreshToken(user);

        verify(jwtService).getAccessTokenExpirationInSeconds();
    }

    @Test
    void shouldReturnConfiguredExpiration() {

        LoginRequest request = createLoginRequest();

        User user = createUser();

        mockSuccessfulAuthentication(user);

        mockJwtGeneration(user);

        LoginResponse response = authService.login(request);

        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    void shouldPropagateAuthenticationException() {

        LoginRequest request = new LoginRequest(
                "sadab@example.com",
                "wrong-password"
        );

        BadCredentialsException exception =
                new BadCredentialsException("Bad credentials");

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(exception);

        assertThatThrownBy(() ->
                authService.login(request)
        )
                .isSameAs(exception);
    }

    @Test
    void shouldNotGenerateTokensWhenAuthenticationFails() {

        LoginRequest request = new LoginRequest(
                "sadab@example.com",
                "wrong-password"
        );

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() ->
                authService.login(request)
        )
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateAccessToken(any(User.class));
        verify(jwtService, never()).generateRefreshToken(any(User.class));
        verify(jwtService, never()).getAccessTokenExpirationInSeconds();
    }

    // ------------------------------------------------------------------------
    // Test Helpers
    // ------------------------------------------------------------------------
    private RegisterRequest createRegisterRequest() {
        return new RegisterRequest(
                "Sadab",
                "Azhar",
                "sadab@example.com",
                "password123"
        );
    }

    private User createUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .firstName("Sadab")
                .lastName("Azhar")
                .email("sadab@example.com")
                .role(Role.USER)
                .build();
    }

    private LoginRequest createLoginRequest() {
        return new LoginRequest(
                "sadab@example.com",
                "password123"
        );
    }

    private Authentication createAuthentication(User user) {

        RapidLinkUserDetails userDetails = new RapidLinkUserDetails(user);

        Authentication authentication = mock(Authentication.class);

        when(authentication.getPrincipal())
                .thenReturn(userDetails);

        return authentication;
    }

    private void mockSuccessfulAuthentication(User user) {

        Authentication authentication = createAuthentication(user);

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authentication);
    }

    private void mockJwtGeneration(User user) {

        when(jwtService.generateAccessToken(user))
                .thenReturn("access-token");

        when(jwtService.generateRefreshToken(user))
                .thenReturn("refresh-token");

        when(jwtService.getAccessTokenExpirationInSeconds())
                .thenReturn(900L);
    }
}