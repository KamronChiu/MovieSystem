package com.eduaccess.ui;

import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;

@Route("login")
public class LoginView extends Div implements HasUrlParameter<String> {

    private final LoginService loginService;
    private String redirectUrl;

    public LoginView(LoginService loginService) {
        this.loginService = loginService;
        
        setSizeFull();
        getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("background", "linear-gradient(135deg, #020b1d 0%, #0a1628 100%)");

        Div loginBox = new Div();
        loginBox.getStyle()
                .set("width", "420px")
                .set("padding", "48px")
                .set("background", "rgba(255,255,255,0.05)")
                .set("border-radius", "16px")
                .set("border", "1px solid rgba(255,255,255,0.1)")
                .set("backdrop-filter", "blur(20px)");

        Span title = new Span("Sign In");
        title.getStyle()
                .set("display", "block")
                .set("font-size", "32px")
                .set("font-weight", "900")
                .set("color", "white")
                .set("text-align", "center")
                .set("margin-bottom", "8px");

        Span subtitle = new Span("Welcome back to HCBS");
        subtitle.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("color", "rgba(255,255,255,0.6)")
                .set("text-align", "center")
                .set("margin-bottom", "36px");

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

        PasswordField passwordField = new PasswordField("Password");
        passwordField.setWidthFull();
        passwordField.getStyle()
                .set("margin-bottom", "24px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff");

        Button loginButton = new Button("Login");
        loginButton.setWidthFull();
        loginButton.getStyle()
                .set("height", "48px")
                .set("background", "#0072ce")
                .set("color", "white")
                .set("font-size", "16px")
                .set("font-weight", "800")
                .set("border-radius", "8px")
                .set("margin-bottom", "24px");

        loginButton.addClickListener(event -> {
            String username = usernameField.getValue();
            String password = passwordField.getValue();

            if (username.isBlank() || password.isBlank()) {
                Notification.show("Please enter username and password", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            if (loginService.login(username, password)) {
                Notification.show("Login successful!", 2000, Notification.Position.TOP_CENTER);
                String targetUrl = redirectUrl != null && !redirectUrl.isEmpty() ? redirectUrl : "home";
                UI.getCurrent().navigate(targetUrl);
            } else {
                Notification.show("Invalid username or password", 3000, Notification.Position.TOP_CENTER);
            }
        });

        Div registerLink = new Div();
        registerLink.getStyle()
                .set("text-align", "center");

        RouterLink link = new RouterLink("Don't have an account? Register here", RegisterView.class);
        link.getStyle()
                .set("color", "#0099ff")
                .set("text-decoration", "none")
                .set("font-weight", "600");

        registerLink.add(link);

        loginBox.add(title, subtitle, usernameField, passwordField, loginButton, registerLink);
        add(loginBox);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
        this.redirectUrl = parameter;
    }

    public static void openLoginWithRedirect(String redirectUrl) {
        VaadinSession.getCurrent().setAttribute("redirectAfterLogin", redirectUrl);
        UI.getCurrent().navigate("login/" + (redirectUrl != null ? redirectUrl : ""));
    }

    public static void showLoginDialog() {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("420px");
        dialog.getStyle().set("border-radius", "16px");

        Div content = new Div();
        content.getStyle()
                .set("padding", "32px")
                .set("background", "#020b1d");

        Span title = new Span("Please Login");
        title.getStyle()
                .set("display", "block")
                .set("font-size", "24px")
                .set("font-weight", "900")
                .set("color", "white")
                .set("margin-bottom", "24px");

        TextField usernameField = new TextField("Username");
        usernameField.setWidthFull();

        PasswordField passwordField = new PasswordField("Password");
        passwordField.setWidthFull();

        Button loginButton = new Button("Login");
        loginButton.getStyle()
                .set("margin-top", "20px")
                .set("width", "100%")
                .set("height", "42px")
                .set("background", "#0072ce")
                .set("color", "white")
                .set("font-weight", "800");

        LoginService loginService = new LoginService(null);

        loginButton.addClickListener(event -> {
            String username = usernameField.getValue();
            String password = passwordField.getValue();
            
            if (loginService.login(username, password)) {
                dialog.close();
                UI.getCurrent().getPage().reload();
            } else {
                Notification.show("Invalid credentials", 3000, Notification.Position.TOP_CENTER);
            }
        });

        content.add(title, usernameField, passwordField, loginButton);
        dialog.add(content);
        dialog.open();
    }
}
