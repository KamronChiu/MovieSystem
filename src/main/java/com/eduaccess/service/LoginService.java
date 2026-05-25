package com.eduaccess.service;

import com.eduaccess.domain.UserAccount;
import com.eduaccess.domain.UserRole;
import com.eduaccess.repository.UserAccountRepository;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LoginService {

    private final UserAccountRepository userAccountRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public LoginService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public boolean login(String username, String password) {
        Optional<UserAccount> userOpt = userAccountRepository.findByUsername(username);
        
        if (userOpt.isPresent()) {
            UserAccount user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                VaadinSession.getCurrent().setAttribute("currentUser", user);
                return true;
            }
        }
        return false;
    }

    public void logout() {
        VaadinSession.getCurrent().setAttribute("currentUser", null);
    }

    public UserAccount getCurrentUser() {
        return (UserAccount) VaadinSession.getCurrent().getAttribute("currentUser");
    }

    public boolean isLoggedIn() {
        return getCurrentUser() != null;
    }

    public UserRole getCurrentUserRole() {
        UserAccount user = getCurrentUser();
        return user != null ? user.getRole() : null;
    }

    public boolean register(String username, String password, String email, String fullName, UserRole role) {
        if (userAccountRepository.existsByUsername(username)) {
            return false;
        }
        if (userAccountRepository.existsByEmail(email)) {
            return false;
        }

        UserAccount newUser = new UserAccount();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setEmail(email);
        newUser.setFullName(fullName);
        newUser.setRole(role);

        userAccountRepository.save(newUser);
        return true;
    }

    public boolean hasRole(UserRole requiredRole) {
        UserAccount user = getCurrentUser();
        if (user == null) {
            return false;
        }
        UserRole userRole = user.getRole();
        
        if (userRole == UserRole.MANAGER) {
            return true;
        }
        if (userRole == UserRole.ADMIN && requiredRole != UserRole.MANAGER) {
            return true;
        }
        if (userRole == UserRole.BOOKING_STAFF && 
            (requiredRole == UserRole.BOOKING_STAFF)) {
            return true;
        }
        return false;
    }

    public boolean canAccessBooking() {
        return hasRole(UserRole.BOOKING_STAFF);
    }

    public boolean canAccessCancellation() {
        return hasRole(UserRole.BOOKING_STAFF);
    }

    public boolean canAccessAdmin() {
        return hasRole(UserRole.ADMIN);
    }

    public boolean canAccessManager() {
        return hasRole(UserRole.MANAGER);
    }
}
