package com.rapidlink.config;

import com.rapidlink.security.CustomUserDetailsService;
import com.rapidlink.security.JwtAuthenticationEntryPoint;
import com.rapidlink.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    /**
     * Password encoder used to securely hash user passwords before storing
     * them in the database.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the Spring Security filter chain.
     *
     * Configuration:
     * - Disables CSRF for the stateless REST API
     * - Uses JWT-based authentication
     * - Does not create HTTP sessions
     * - Exposes selected public endpoints
     * - Requires authentication for all other requests
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Disable CSRF since the API uses JWT instead of session cookies.
                .csrf(AbstractHttpConfigurer::disable)

                // Do not create or use HTTP sessions.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth

                        // Public authentication endpoints.
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()

                        // Public URL redirection endpoint.
                        // Anyone with a short URL can access the destination.
                        .requestMatchers(HttpMethod.GET, "/{shortCode}").permitAll()

                        // Allow anonymous users to create demo short URLs.
                        .requestMatchers(HttpMethod.POST, "/api/urls").permitAll()

                        // Actuator endpoints used for monitoring and health checks.
                        .requestMatchers("/actuator/**").permitAll()

                        // All remaining endpoints require authentication.
                        .anyRequest().authenticated()
                )

                // Handle authentication failures for protected resources.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                )

                // Register JWT authentication filter.
                // It runs before UsernamePasswordAuthenticationFilter and
                // populates the SecurityContext from the JWT token.
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * Configures how Spring Security authenticates users.
     * It uses our CustomUserDetailsService to load users
     * and PasswordEncoder to verify passwords.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        // Load user details from the database
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider();

        // Compare the raw password with the stored password hash
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    /**
     * Exposes Spring Security's AuthenticationManager as a bean.
     * It is used to authenticate a user's email and password during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }
}
