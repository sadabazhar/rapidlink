package com.rapidlink.security;

import com.rapidlink.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

/**
 * Wraps the application's User object so Spring Security can use it
 * to authenticate users and check their permissions.
 */
@Getter
@RequiredArgsConstructor
public final class RapidLinkUserDetails implements UserDetails {

    /**
     * -- GETTER --
     *  Returns the original User object.
     */
    private final User user;

    /**
     * Returns the user's email, which is used as the login identifier.
     */
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    /**
     * Returns the user's hashed password for authentication.
     */
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /**
     * Returns the user's role as a Spring Security authority.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    /**
     * Indicates whether the user's account is enabled.
     */
    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    /**
     * Indicates whether the user's account is not locked.
     */
    @Override
    public boolean isAccountNonLocked() {
        return !user.isAccountLocked();
    }

}
