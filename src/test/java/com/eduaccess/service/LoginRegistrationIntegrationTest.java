package com.eduaccess.service;

import com.eduaccess.domain.UserAccount;
import com.eduaccess.domain.UserRole;
import com.eduaccess.repository.UserAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_013 — Integration test for the Login/Registration flow.
 * <p>
 * Tests the full registration cycle end-to-end with a real Spring context
 * and in-memory H2 database. Verifies:
 * <ul>
 *   <li>Successful registration persists a properly-hashed user</li>
 *   <li>Duplicate username / email are rejected</li>
 *   <li>The persisted password can be verified by BCryptPasswordEncoder</li>
 *   <li>Role assignment is correct</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:login-test;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "vaadin.launch-browser=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class LoginRegistrationIntegrationTest {

    @Autowired
    private LoginService loginService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("register_fullFlow_persistsUserWithHashedPassword")
    void register_fullFlow_persistsUserWithHashedPassword() {
        boolean result = loginService.register(
                "testuser", "SecurePass123", "test@example.com",
                "Test User", UserRole.BOOKING_STAFF);

        assertThat(result).isTrue();

        // Verify persistence
        Optional<UserAccount> found = userAccountRepository.findByUsername("testuser");
        assertThat(found).isPresent();

        UserAccount user = found.get();
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getFullName()).isEqualTo("Test User");
        assertThat(user.getRole()).isEqualTo(UserRole.BOOKING_STAFF);

        // Verify password is hashed, not plain
        assertThat(user.getPassword()).isNotEqualTo("SecurePass123");
        assertThat(encoder.matches("SecurePass123", user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("register_duplicateUsername_rejectsSecondAttempt")
    void register_duplicateUsername_rejectsSecondAttempt() {
        // First registration succeeds
        boolean first = loginService.register(
                "duplicateUser", "pass1", "first@test.com", "First", UserRole.BOOKING_STAFF);
        assertThat(first).isTrue();

        // Second registration with same username fails
        boolean second = loginService.register(
                "duplicateUser", "pass2", "second@test.com", "Second", UserRole.ADMIN);
        assertThat(second).isFalse();

        // Only one user should exist
        assertThat(userAccountRepository.findByUsername("duplicateUser"))
                .isPresent()
                .get()
                .extracting(UserAccount::getEmail)
                .isEqualTo("first@test.com");
    }

    @Test
    @DisplayName("register_duplicateEmail_rejectsSecondAttempt")
    void register_duplicateEmail_rejectsSecondAttempt() {
        boolean first = loginService.register(
                "user1", "pass1", "shared@test.com", "User1", UserRole.BOOKING_STAFF);
        assertThat(first).isTrue();

        boolean second = loginService.register(
                "user2", "pass2", "shared@test.com", "User2", UserRole.ADMIN);
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("register_managerRole_persistsCorrectly")
    void register_managerRole_persistsCorrectly() {
        boolean result = loginService.register(
                "mgr1", "mgrPass", "mgr@cinema.com", "Manager One", UserRole.MANAGER);

        assertThat(result).isTrue();
        Optional<UserAccount> found = userAccountRepository.findByUsername("mgr1");
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(UserRole.MANAGER);
    }

    @Test
    @DisplayName("register_adminRole_persistsCorrectly")
    void register_adminRole_persistsCorrectly() {
        boolean result = loginService.register(
                "adm1", "admPass", "adm@cinema.com", "Admin One", UserRole.ADMIN);

        assertThat(result).isTrue();
        Optional<UserAccount> found = userAccountRepository.findByUsername("adm1");
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("register_thenVerifyPassword_matchesWithEncoder")
    void register_thenVerifyPassword_matchesWithEncoder() {
        String rawPassword = "MyP@ssw0rd!";
        loginService.register("verifyuser", rawPassword, "v@t.com", "V", UserRole.BOOKING_STAFF);

        UserAccount user = userAccountRepository.findByUsername("verifyuser").orElseThrow();

        // The stored BCrypt hash must verify against the original password
        assertThat(encoder.matches(rawPassword, user.getPassword())).isTrue();
        // And must NOT match a wrong password
        assertThat(encoder.matches("WrongPassword", user.getPassword())).isFalse();
    }
}
