package com.rapidlink.repository;

import com.rapidlink.entity.User;
import com.rapidlink.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .firstName("Sadab")
                .lastName("Azhar")
                .email("sadab@example.com")
                .passwordHash("hashedPassword")
                .role(Role.USER)
                .emailVerified(true)
                .enabled(true)
                .accountLocked(false)
                .build();

        user = userRepository.saveAndFlush(user);
        entityManager.clear();
    }

    // ------------------------------------------------------------------------
    // Repository query tests
    // ------------------------------------------------------------------------
    @Test
    void shouldSaveUserSuccessfully() {
        User newUser = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .passwordHash("hashedPassword")
                .build();

        User savedUser = userRepository.saveAndFlush(newUser);

        entityManager.clear();

        User persistedUser = userRepository.findById(savedUser.getId()).orElseThrow();

        assertThat(persistedUser.getId()).isNotNull();
        assertThat(persistedUser.getCreatedAt()).isNotNull();
        assertThat(persistedUser.getUpdatedAt()).isNotNull();
        assertThat(persistedUser.getRole()).isEqualTo(Role.USER);
        assertThat(persistedUser.isEnabled()).isTrue();
        assertThat(persistedUser.isEmailVerified()).isFalse();
        assertThat(persistedUser.isAccountLocked()).isFalse();
    }

    @Test
    void shouldFindUserByEmail() {
        Optional<User> foundUser = userRepository.findByEmail(user.getEmail());

        assertThat(foundUser)
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo(user.getEmail());
    }

    @Test
    void shouldReturnEmptyWhenEmailDoesNotExist() {
        Optional<User> foundUser = userRepository.findByEmail("unknown@example.com");

        assertThat(foundUser).isEmpty();
    }

    @Test
    void shouldReturnTrueWhenEmailExists() {
        assertThat(userRepository.existsByEmail(user.getEmail())).isTrue();
    }

    @Test
    void shouldReturnFalseWhenEmailDoesNotExist() {
        assertThat(userRepository.existsByEmail("unknown@example.com")).isFalse();
    }

    // ------------------------------------------------------------------------
    // Database constraint tests
    // ------------------------------------------------------------------------

    @Test
    void shouldThrowExceptionWhenEmailIsDuplicate() {
        User duplicateUser = User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email(user.getEmail())
                .passwordHash("password")
                .build();

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(duplicateUser);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldThrowExceptionWhenFirstNameIsNull() {
        User invalidUser = User.builder()
                .lastName("Doe")
                .email("john@example.com")
                .passwordHash("password")
                .build();

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(invalidUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldThrowExceptionWhenLastNameIsNull() {
        User invalidUser = User.builder()
                .firstName("john")
                .email("john@example.com")
                .passwordHash("password")
                .build();

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(invalidUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldThrowExceptionWhenEmailIsNull() {
        User invalidUser = User.builder()
                .firstName("john")
                .lastName("Doe")
                .passwordHash("password")
                .build();

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(invalidUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldThrowExceptionWhenPasswordHashIsNull() {
        User invalidUser = User.builder()
                .firstName("john")
                .lastName("Doe")
                .email("john@example.com")
                .build();

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(invalidUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------------
    // Update tests
    // ------------------------------------------------------------------------
    @Test
    void shouldUpdateExistingUser() {
        LocalDateTime previousUpdatedAt = user.getUpdatedAt();

        user.setFirstName("Updated");

        User updatedUser = userRepository.saveAndFlush(user);

        entityManager.clear();

        User reloadedUser = userRepository.findById(updatedUser.getId())
                .orElseThrow();

        assertThat(reloadedUser.getFirstName()).isEqualTo("Updated");
        assertThat(reloadedUser.getLastName()).isEqualTo("Azhar");
        assertThat(reloadedUser.getEmail()).isEqualTo("sadab@example.com");
        assertThat(reloadedUser.getUpdatedAt())
                .isAfterOrEqualTo(previousUpdatedAt);
    }
}
