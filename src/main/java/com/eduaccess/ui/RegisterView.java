package com.eduaccess.ui;

import com.eduaccess.domain.UserRole;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

@Route("register")
public class RegisterView extends Div {

    private final LoginService loginService;

    public RegisterView(LoginService loginService) {
        this.loginService = loginService;

        setSizeFull();
        getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("background", "linear-gradient(135deg, #020b1d 0%, #0a1628 100%)");

        Div registerBox = new Div();
        registerBox.getStyle()
                .set("width", "480px")
                .set("padding", "48px")
                .set("background", "rgba(255,255,255,0.05)")
                .set("border-radius", "16px")
                .set("border", "1px solid rgba(255,255,255,0.1)")
                .set("backdrop-filter", "blur(20px)");

        Span title = new Span("Create Account");
        title.getStyle()
                .set("display", "block")
                .set("font-size", "32px")
                .set("font-weight", "900")
                .set("color", "white")
                .set("text-align", "center")
                .set("margin-bottom", "8px");

        Span subtitle = new Span("Join Horizon Cinemas Booking System");
        subtitle.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("color", "rgba(255,255,255,0.6)")
                .set("text-align", "center")
                .set("margin-bottom", "36px");

        TextField fullNameField = new TextField("Full Name");
        fullNameField.setWidthFull();
        fullNameField.getStyle()
                .set("margin-bottom", "16px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff");

        TextField usernameField = new TextField("Username");
        usernameField.setWidthFull();
        usernameField.getStyle()
                .set("margin-bottom", "16px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff");

        EmailField emailField = new EmailField("Email");
        emailField.setWidthFull();
        emailField.getStyle()
                .set("margin-bottom", "16px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff");

        PasswordField passwordField = new PasswordField("Password");
        passwordField.setWidthFull();
        passwordField.getStyle()
                .set("margin-bottom", "16px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff");

        PasswordField confirmPasswordField = new PasswordField("Confirm Password");
        confirmPasswordField.setWidthFull();
        confirmPasswordField.getStyle()
                .set("margin-bottom", "20px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff");

        ComboBox<UserRole> roleComboBox = new ComboBox<>("User Role");
        roleComboBox.setWidthFull();
        roleComboBox.setItems(UserRole.values());
        roleComboBox.setItemLabelGenerator(role -> {
            return switch (role) {
                case BOOKING_STAFF -> "Booking Staff (售票员)";
                case ADMIN -> "Admin (管理员)";
                case MANAGER -> "Manager (经理)";
            };
        });
        roleComboBox.setValue(UserRole.BOOKING_STAFF);
        roleComboBox.getStyle()
                .set("margin-bottom", "24px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff");

        Button registerButton = new Button("Register");
        registerButton.setWidthFull();
        registerButton.getStyle()
                .set("height", "48px")
                .set("background", "#0072ce")
                .set("color", "white")
                .set("font-size", "16px")
                .set("font-weight", "800")
                .set("border-radius", "8px")
                .set("margin-bottom", "24px");

        registerButton.addClickListener(event -> {
            String fullName = fullNameField.getValue();
            String username = usernameField.getValue();
            String email = emailField.getValue();
            String password = passwordField.getValue();
            String confirmPassword = confirmPasswordField.getValue();
            UserRole role = roleComboBox.getValue();

            if (fullName.isBlank() || username.isBlank() || email.isBlank() || password.isBlank()) {
                Notification.show("Please fill in all fields", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            if (!password.equals(confirmPassword)) {
                Notification.show("Passwords do not match", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            if (password.length() < 6) {
                Notification.show("Password must be at least 6 characters", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            if (loginService.register(username, password, email, fullName, role)) {
                Notification.show("Registration successful! Please login.", 3000, Notification.Position.TOP_CENTER);
                UI.getCurrent().navigate("login");
            } else {
                Notification.show("Username or email already exists", 3000, Notification.Position.TOP_CENTER);
            }
        });

        Div loginLink = new Div();
        loginLink.getStyle()
                .set("text-align", "center");

        RouterLink link = new RouterLink("Already have an account? Login here", LoginView.class);
        link.getStyle()
                .set("color", "#0099ff")
                .set("text-decoration", "none")
                .set("font-weight", "600");

        loginLink.add(link);

        registerBox.add(title, subtitle, fullNameField, usernameField, emailField, 
                passwordField, confirmPasswordField, roleComboBox, registerButton, loginLink);
        add(registerBox);
    }
}
