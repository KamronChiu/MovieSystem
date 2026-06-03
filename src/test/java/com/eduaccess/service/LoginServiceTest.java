package com.eduaccess.service;

import com.eduaccess.domain.UserAccount;
import com.eduaccess.domain.UserRole;
import com.eduaccess.repository.UserAccountRepository;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UT_013 ~ UT_018 — Unit tests for {@link LoginService}.
 * <p>
 * Covers:
 * <ul>
 *   <li>Registration: success, duplicate username, duplicate email</li>
 *   <li>Login: valid credentials, invalid password, non-existent user</li>
 *   <li>Role hierarchy: MANAGER > ADMIN > BOOKING_STAFF</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    private LoginService loginService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        loginService = new LoginService(userAccountRepository);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Registration Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("register_newUser_returnsTrue")
        void register_newUser_returnsTrue() {
            when(userAccountRepository.existsByUsername("newuser")).thenReturn(false);
            when(userAccountRepository.existsByEmail("new@test.com")).thenReturn(false);
            when(userAccountRepository.save(any(UserAccount.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            boolean result = loginService.register(
                    "newuser", "password123", "new@test.com", "New User", UserRole.BOOKING_STAFF);

            assertThat(result).isTrue();
            verify(userAccountRepository).save(any(UserAccount.class));
        }

        @Test
        @DisplayName("register_duplicateUsername_returnsFalse")
        void register_duplicateUsername_returnsFalse() {
            when(userAccountRepository.existsByUsername("existing")).thenReturn(true);

            boolean result = loginService.register(
                    "existing", "password123", "unique@test.com", "User", UserRole.BOOKING_STAFF);

            assertThat(result).isFalse();
            verify(userAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("register_duplicateEmail_returnsFalse")
        void register_duplicateEmail_returnsFalse() {
            when(userAccountRepository.existsByUsername("unique")).thenReturn(false);
            when(userAccountRepository.existsByEmail("taken@test.com")).thenReturn(true);

            boolean result = loginService.register(
                    "unique", "password123", "taken@test.com", "User", UserRole.ADMIN);

            assertThat(result).isFalse();
            verify(userAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("register_passwordIsHashed_notStoredPlain")
        void register_passwordIsHashed_notStoredPlain() {
            when(userAccountRepository.existsByUsername(anyString())).thenReturn(false);
            when(userAccountRepository.existsByEmail(anyString())).thenReturn(false);
            when(userAccountRepository.save(any(UserAccount.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            loginService.register("user", "mySecret", "u@t.com", "U", UserRole.BOOKING_STAFF);

            verify(userAccountRepository).save(argThat(account -> {
                // The stored password must NOT be the plain text
                assertThat(account.getPassword()).isNotEqualTo("mySecret");
                // It should be a BCrypt hash (starts with $2a$ or $2b$)
                assertThat(account.getPassword()).startsWith("$2");
                return true;
            }));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Login Tests (requires mocking VaadinSession static)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Login")
    class LoginTests {

        @Test
        @DisplayName("login_validCredentials_returnsTrue")
        void login_validCredentials_returnsTrue() {
            String rawPassword = "correctPassword";
            String hashed = encoder.encode(rawPassword);

            UserAccount user = new UserAccount("alice", hashed, "alice@test.com",
                    "Alice", UserRole.BOOKING_STAFF);
            when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            try (MockedStatic<VaadinSession> sessionMock = mockStatic(VaadinSession.class)) {
                VaadinSession mockSession = mock(VaadinSession.class);
                sessionMock.when(VaadinSession::getCurrent).thenReturn(mockSession);

                boolean result = loginService.login("alice", rawPassword);

                assertThat(result).isTrue();
                verify(mockSession).setAttribute("currentUser", user);
            }
        }

        @Test
        @DisplayName("login_wrongPassword_returnsFalse")
        void login_wrongPassword_returnsFalse() {
            String hashed = encoder.encode("correctPassword");
            UserAccount user = new UserAccount("bob", hashed, "bob@test.com",
                    "Bob", UserRole.ADMIN);
            when(userAccountRepository.findByUsername("bob")).thenReturn(Optional.of(user));

            try (MockedStatic<VaadinSession> sessionMock = mockStatic(VaadinSession.class)) {
                VaadinSession mockSession = mock(VaadinSession.class);
                sessionMock.when(VaadinSession::getCurrent).thenReturn(mockSession);

                boolean result = loginService.login("bob", "wrongPassword");

                assertThat(result).isFalse();
                verify(mockSession, never()).setAttribute(anyString(), any());
            }
        }

        @Test
        @DisplayName("login_nonExistentUser_returnsFalse")
        void login_nonExistentUser_returnsFalse() {
            when(userAccountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            boolean result = loginService.login("ghost", "anyPassword");

            assertThat(result).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Role Hierarchy Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Role Hierarchy")
    class RoleHierarchyTests {

        @Test
        @DisplayName("hasRole_managerCanAccessAll")
        void hasRole_managerCanAccessAll() {
            assertThat(loginService.hasRole(UserRole.MANAGER, UserRole.MANAGER)).isTrue();
            assertThat(loginService.hasRole(UserRole.MANAGER, UserRole.ADMIN)).isTrue();
            assertThat(loginService.hasRole(UserRole.MANAGER, UserRole.BOOKING_STAFF)).isTrue();
        }

        @Test
        @DisplayName("hasRole_adminCannotAccessManager")
        void hasRole_adminCannotAccessManager() {
            assertThat(loginService.hasRole(UserRole.ADMIN, UserRole.ADMIN)).isTrue();
            assertThat(loginService.hasRole(UserRole.ADMIN, UserRole.BOOKING_STAFF)).isTrue();
            assertThat(loginService.hasRole(UserRole.ADMIN, UserRole.MANAGER)).isFalse();
        }

        @Test
        @DisplayName("hasRole_bookingStaffOnlyOwnLevel")
        void hasRole_bookingStaffOnlyOwnLevel() {
            assertThat(loginService.hasRole(UserRole.BOOKING_STAFF, UserRole.BOOKING_STAFF)).isTrue();
            assertThat(loginService.hasRole(UserRole.BOOKING_STAFF, UserRole.ADMIN)).isFalse();
            assertThat(loginService.hasRole(UserRole.BOOKING_STAFF, UserRole.MANAGER)).isFalse();
        }

        @Test
        @DisplayName("hasRole_nullRole_returnsFalse")
        void hasRole_nullRole_returnsFalse() {
            assertThat(loginService.hasRole(null, UserRole.BOOKING_STAFF)).isFalse();
        }
    }
}
