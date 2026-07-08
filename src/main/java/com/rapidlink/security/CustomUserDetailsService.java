package com.rapidlink.security;

import com.rapidlink.entity.User;
import com.rapidlink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.UUID;

/**
 * Service responsible for loading application users and adapting them
 * to Spring Security's {@link UserDetails} model.
 *
 * <p>This service is used by multiple authentication mechanisms:
 * <ul>
 *     <li>Email/password authentication during login.</li>
 *     <li>JWT authentication when validating access tokens.</li>
 * </ul>
 *
 * <p>Each loaded {@link User} is wrapped in a
 * {@link RapidLinkUserDetails} instance so Spring Security can perform
 * authentication and authorization checks consistently.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by email address and returns it as a
     * {@link UserDetails} instance.
     *
     * <p>This method is used by Spring Security during the
     * email/password authentication process.
     *
     * @param email the user's email address
     * @return authenticated user details
     * @throws UsernameNotFoundException if no user exists with the given email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        return new RapidLinkUserDetails(user);
    }


    /**
     * Loads a user by unique identifier and returns it as a
     * {@link UserDetails} instance.
     *
     * <p>This method is primarily used during JWT authentication,
     * where the token subject contains the user's ID. The returned
     * user is wrapped in a {@link RapidLinkUserDetails} object so
     * Spring Security can create an authenticated principal.
     *
     * @param userId the user's unique identifier
     * @return authenticated user details wrapped in a {@link RapidLinkUserDetails} instance
     * @throws UsernameNotFoundException if no user exists with the given ID
     */
    public RapidLinkUserDetails loadUserById(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));

        return new RapidLinkUserDetails(user);
    }
}
