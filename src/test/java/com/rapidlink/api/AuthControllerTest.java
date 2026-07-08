package com.rapidlink.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapidlink.controller.AuthController;
import com.rapidlink.dto.request.auth.LoginRequest;
import com.rapidlink.dto.request.auth.RegisterRequest;
import com.rapidlink.dto.response.auth.LoginResponse;
import com.rapidlink.dto.response.auth.RegisterResponse;
import com.rapidlink.enums.Role;
import com.rapidlink.exception.EmailAlreadyExistsException;
import com.rapidlink.exception.GlobalExceptionHandler;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.security.JwtAuthenticationFilter;
import com.rapidlink.services.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RapidLinkMetrics rapidLinkMetrics;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // ------------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------------

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {

        RegisterRequest request = createRegisterRequest();

        RegisterResponse response = createRegisterResponse();

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.firstName").value("Sadab"))
                .andExpect(jsonPath("$.lastName").value("Azhar"))
                .andExpect(jsonPath("$.email").value("sadab@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void shouldReturnBadRequestWhenRegisterRequestIsInvalid() throws Exception {

        RegisterRequest request = new RegisterRequest(
                "",
                "",
                "invalid-email",
                ""
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void shouldPassRequestToService() throws Exception {

        RegisterRequest request = createRegisterRequest();

        RegisterResponse response = createRegisterResponse();

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterRequest> captor =
                ArgumentCaptor.forClass(RegisterRequest.class);

        verify(authService).register(captor.capture());

        RegisterRequest captured = captor.getValue();

        assertThat(captured.firstName()).isEqualTo(request.firstName());
        assertThat(captured.lastName()).isEqualTo(request.lastName());
        assertThat(captured.email()).isEqualTo(request.email());
        assertThat(captured.password()).isEqualTo(request.password());
    }

    // ------------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------------

    @Test
    void shouldLoginSuccessfully() throws Exception {

        LoginRequest request = createLoginRequest();

        LoginResponse response = createLoginResponse();

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void shouldReturnBadRequestWhenLoginRequestIsInvalid() throws Exception {

        LoginRequest request = new LoginRequest(
                "invalid-email",
                ""
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void shouldPassLoginRequestToService() throws Exception {

        LoginRequest request = createLoginRequest();

        LoginResponse response = createLoginResponse();

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> captor =
                ArgumentCaptor.forClass(LoginRequest.class);

        verify(authService).login(captor.capture());

        LoginRequest captured = captor.getValue();

        assertThat(captured.email()).isEqualTo(request.email());
        assertThat(captured.password()).isEqualTo(request.password());
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {

        RegisterRequest request = createRegisterRequest();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException(
                        "An account with this email already exists."
                ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {

        LoginRequest request = createLoginRequest();

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(authService).login(any(LoginRequest.class));
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

    private LoginRequest createLoginRequest() {
        return new LoginRequest(
                "sadab@example.com",
                "password123"
        );
    }

    private RegisterResponse createRegisterResponse() {
        return new RegisterResponse(
                UUID.randomUUID(),
                "Sadab",
                "Azhar",
                "sadab@example.com",
                Role.USER
        );
    }

    private LoginResponse createLoginResponse() {
        return new LoginResponse(
                "access-token",
                "refresh-token",
                "Bearer",
                900L
        );
    }
}
