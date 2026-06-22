package com.rapidlink.config;

import com.rapidlink.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Central Spring Security configuration.
 */
@Configuration
public class SecurityConfig {

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
     * Current configuration:
     * - Disables CSRF (JWT-based REST API)
     * - Uses stateless session management
     * - Allows all requests temporarily until JWT authentication is implemented
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

                // Allow all endpoints temporarily.
                // This will be replaced with authenticated access after JWT is implemented.
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll()
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
