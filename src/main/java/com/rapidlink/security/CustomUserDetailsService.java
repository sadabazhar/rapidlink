package com.rapidlink.security;

import com.rapidlink.entity.User;
import com.rapidlink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Service responsible for loading user details from the database during
 * the authentication process. Spring Security uses this service to retrieve
 * a user by email and create a {@link UserDetails} object for authentication.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Finds a user by email and returns it as a {@link UserDetails} instance.
     *
     * @param email the user's email address used for authentication
     * @return the authenticated user's details
     * @throws UsernameNotFoundException if no user exists with the given email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        return new RapidLinkUserDetails(user);
    }
}
