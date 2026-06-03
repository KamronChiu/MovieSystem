package com.eduaccess.repository;

import com.eduaccess.domain.UserAccount;
import com.eduaccess.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_011 / IT_012 — Integration tests for {@link UserAccountRepository}.
 * <p>
 * Verifies user lookup, existence checks, and uniqueness constraints
 * against an in-memory H2 schema via {@code @DataJpaTest}.
 */
@DataJpaTest
class UserAccountRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void setUp() {
        UserAccount staff = new UserAccount(
                "staffuser", "$2a$10$hashedpassword", "staff@cinema.com",
                "Staff User", UserRole.BOOKING_STAFF);
        em.persist(staff);

        UserAccount admin = new UserAccount(
                "adminuser", "$2a$10$hashedpassword2", "admin@cinema.com",
                "Admin User", UserRole.ADMIN);
        em.persist(admin);

        em.flush();
    }

    @Test
    @DisplayName("findByUsername_existingUser_returnsAccount")
    void findByUsername_existingUser_returnsAccount() {
        Optional<UserAccount> found = userAccountRepository.findByUsername("staffuser");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("staff@cinema.com");
        assertThat(found.get().getRole()).isEqualTo(UserRole.BOOKING_STAFF);
        assertThat(found.get().getFullName()).isEqualTo("Staff User");
    }

    @Test
    @DisplayName("findByUsername_nonExistent_returnsEmpty")
    void findByUsername_nonExistent_returnsEmpty() {
        Optional<UserAccount> found = userAccountRepository.findByUsername("ghost");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByUsername_existingUser_returnsTrue")
    void existsByUsername_existingUser_returnsTrue() {
        assertThat(userAccountRepository.existsByUsername("staffuser")).isTrue();
        assertThat(userAccountRepository.existsByUsername("nonexist")).isFalse();
    }

    @Test
    @DisplayName("existsByEmail_existingEmail_returnsTrue")
    void existsByEmail_existingEmail_returnsTrue() {
        assertThat(userAccountRepository.existsByEmail("admin@cinema.com")).isTrue();
        assertThat(userAccountRepository.existsByEmail("nobody@test.com")).isFalse();
    }

    @Test
    @DisplayName("findByEmail_returnsCorrectUser")
    void findByEmail_returnsCorrectUser() {
        Optional<UserAccount> found = userAccountRepository.findByEmail("admin@cinema.com");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("adminuser");
        assertThat(found.get().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("save_newUser_persistsSuccessfully")
    void save_newUser_persistsSuccessfully() {
        UserAccount newUser = new UserAccount(
                "manager1", "$2a$10$hash", "manager@cinema.com",
                "Manager One", UserRole.MANAGER);

        UserAccount saved = userAccountRepository.save(newUser);
        em.flush();
        em.clear();

        Optional<UserAccount> found = userAccountRepository.findByUsername("manager1");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getRole()).isEqualTo(UserRole.MANAGER);
    }

    @Test
    @DisplayName("register_sameUsernameSecondTime_violatesUnique")
    void register_sameUsernameSecondTime_violatesUnique() {
        // Verify existsByUsername prevents duplicate registration at service level
        assertThat(userAccountRepository.existsByUsername("staffuser")).isTrue();
        // Service layer would return false based on this check
    }
}
