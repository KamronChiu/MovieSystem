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
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.ClientCallable;

@Route("login")
@CssImport("./styles/custom-styles.css")
public class LoginView extends Div implements HasUrlParameter<String> {

    private final LoginService loginService;
    private String redirectUrl;
    private boolean isLoginMode = true; // true = 登录模式, false = 注册模式
    private boolean isAnimating = false; // 防止动画过程中重复点击

    // 组件引用
    private Div container;
    private Div leftPanel;
    private Div rightPanel;
    private Div formContainer;
    private Div welcomeContainer;

    // 登录表单组件
    private TextField loginUsernameField;
    private PasswordField loginPasswordField;

    // 注册表单组件
    private TextField registerUsernameField;
    private EmailField registerEmailField;
    private PasswordField registerPasswordField;
    private ComboBox<UserRole> roleComboBox;

    public LoginView(LoginService loginService) {
        this.loginService = loginService;

        setSizeFull();
        getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("position", "relative")
                .set("overflow", "hidden");

        // 背景容器
        Div backgroundContainer = new Div();
        backgroundContainer.getStyle()
                .set("position", "absolute")
                .set("top", "0")
                .set("left", "0")
                .set("width", "100%")
                .set("height", "200%")
                .set("background-image", "url('https://pic1.zhimg.com/v2-b12617ee17d977d32eee4f8299d25c17_1440w.jpg?source=172ae18b')")
                .set("background-size", "100% 50%")
                .set("background-repeat", "repeat-y")
                .set("animation", "scrollUp 20s linear infinite");
        add(backgroundContainer);

        // 遮罩层
        Div overlay = new Div();
        overlay.getStyle()
                .set("position", "absolute")
                .set("top", "0")
                .set("left", "0")
                .set("width", "100%")
                .set("height", "100%")
                .set("background", "linear-gradient(135deg, rgba(2, 11, 29, 0.9) 0%, rgba(10, 22, 40, 0.85) 100%)");
        add(overlay);

        // 主容器 - 包含左右两个面板
        container = new Div();
        container.getStyle()
                .set("display", "flex")
                .set("width", "900px")
                .set("height", "500px")
                .set("background", "rgba(255,255,255,0.05)")
                .set("border-radius", "20px")
                .set("border", "1px solid rgba(255,255,255,0.1)")
                .set("backdrop-filter", "blur(20px)")
                .set("overflow", "hidden")
                .set("transition", "all 0.5s cubic-bezier(0.4, 0, 0.2, 1)");

        // 左侧面板
        leftPanel = new Div();
        leftPanel.getStyle()
                .set("width", "50%")
                .set("height", "100%")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("padding", "40px")
                .set("box-sizing", "border-box")
                .set("transition", "all 0.5s cubic-bezier(0.4, 0, 0.2, 1)");

        rightPanel = new Div();
        rightPanel.getStyle()
                .set("width", "50%")
                .set("height", "100%")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("padding", "40px")
                .set("box-sizing", "border-box")
                .set("background", "rgba(255,255,255,0.03)")
                .set("transition", "all 0.5s cubic-bezier(0.4, 0, 0.2, 1)");

        // 创建表单容器和欢迎容器
        createFormContainer();
        createWelcomeContainer();

        // 初始状态：左侧登录表单，右侧欢迎信息
        updateLayout();

        container.add(leftPanel, rightPanel);
        add(container);
    }

    private void createFormContainer() {
        formContainer = new Div();
        formContainer.getStyle()
                .set("width", "100%")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center");

        // 登录表单
        Div loginForm = new Div();
        loginForm.getStyle().set("width", "100%");

        Span loginTitle = new Span("Sign In");
        loginTitle.getStyle()
                .set("display", "block")
                .set("font-size", "28px")
                .set("font-weight", "900")
                .set("color", "white")
                .set("text-align", "center")
                .set("margin-bottom", "8px");

        Span loginSubtitle = new Span("Welcome back to HCBS");
        loginSubtitle.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("color", "rgba(255,255,255,0.6)")
                .set("text-align", "center")
                .set("margin-bottom", "32px");

        loginUsernameField = new TextField("Username");
        loginUsernameField.setWidthFull();
        loginUsernameField.getStyle()
                .set("margin-bottom", "16px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff")
                .set("color", "#ffffff");

        loginPasswordField = new PasswordField("Password");
        loginPasswordField.setWidthFull();
        loginPasswordField.getStyle()
                .set("margin-bottom", "24px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff")
                .set("color", "#ffffff");

        Button loginButton = new Button("Login");
        loginButton.setWidthFull();
        loginButton.getStyle()
                .set("height", "48px")
                .set("background", "#0072ce")
                .set("color", "white")
                .set("font-size", "16px")
                .set("font-weight", "800")
                .set("border-radius", "8px")
                .set("margin-bottom", "16px");

        loginButton.addClickListener(event -> handleLogin());

        loginForm.add(loginTitle, loginSubtitle, loginUsernameField, loginPasswordField, loginButton);

        // 注册表单
        Div registerForm = new Div();
        registerForm.getStyle().set("width", "100%");

        Span registerTitle = new Span("Create Account");
        registerTitle.getStyle()
                .set("display", "block")
                .set("font-size", "28px")
                .set("font-weight", "900")
                .set("color", "white")
                .set("text-align", "center")
                .set("margin-bottom", "20px");

        registerUsernameField = new TextField("Username");
        registerUsernameField.setWidthFull();
        registerUsernameField.getStyle()
                .set("margin-bottom", "12px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff")
                .set("color", "#ffffff");

        registerEmailField = new EmailField("Email");
        registerEmailField.setWidthFull();
        registerEmailField.getStyle()
                .set("margin-bottom", "12px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff")
                .set("color", "#ffffff");

        registerPasswordField = new PasswordField("Password");
        registerPasswordField.setWidthFull();
        registerPasswordField.getStyle()
                .set("margin-bottom", "12px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff")
                .set("color", "#ffffff");

        roleComboBox = new ComboBox<>("User Role");
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
                .set("margin-bottom", "20px")
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.1)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.3)")
                .set("--vaadin-input-field-text-color", "#ffffff")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.9)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.6)")
                .set("--vaadin-input-field-focus-ring-color", "#0099ff")
                .set("color", "#ffffff");

        Button registerButton = new Button("Register");
        registerButton.setWidthFull();
        registerButton.getStyle()
                .set("height", "48px")
                .set("background", "#0072ce")
                .set("color", "white")
                .set("font-size", "16px")
                .set("font-weight", "800")
                .set("border-radius", "8px")
                .set("margin-bottom", "16px");

        registerButton.addClickListener(event -> handleRegister());

        registerForm.add(registerTitle, registerUsernameField, 
                registerEmailField, registerPasswordField, roleComboBox, registerButton);

        // 将两个表单添加到容器
        formContainer.add(loginForm, registerForm);

        // 默认隐藏注册表单
        registerForm.getStyle().set("display", "none");
    }

    private void createWelcomeContainer() {
        welcomeContainer = new Div();
        welcomeContainer.getStyle()
                .set("width", "100%")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("text-align", "center");

        // 欢迎标题
        Span welcomeTitle = new Span("Welcome to HCBS");
        welcomeTitle.getStyle()
                .set("display", "block")
                .set("font-size", "32px")
                .set("font-weight", "900")
                .set("color", "white")
                .set("margin-bottom", "16px");

        // 欢迎描述
        Span welcomeDesc = new Span("Your ultimate cinema booking experience awaits. " +
                "Discover the latest movies and book your seats with ease.");
        welcomeDesc.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("color", "rgba(255,255,255,0.7)")
                .set("line-height", "1.6")
                .set("margin-bottom", "32px");

        // 装饰图标
        Span icon = new Span("🎬");
        icon.getStyle()
                .set("font-size", "64px")
                .set("margin-bottom", "24px");

        // 切换按钮容器
        Div buttonContainer = new Div();
        buttonContainer.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "12px");

        // 登录模式时显示的注册按钮
        Button registerSwitchButton = new Button("Create Account");
        registerSwitchButton.setWidth("200px");
        registerSwitchButton.getStyle()
                .set("height", "48px")
                .set("background", "transparent")
                .set("color", "#0099ff")
                .set("font-size", "16px")
                .set("font-weight", "800")
                .set("border-radius", "8px")
                .set("border", "2px solid #0099ff");

        registerSwitchButton.addClickListener(event -> switchToRegister());

        // 注册模式时显示的登录按钮
        Button loginSwitchButton = new Button("Sign In");
        loginSwitchButton.setWidth("200px");
        loginSwitchButton.getStyle()
                .set("height", "48px")
                .set("background", "transparent")
                .set("color", "#0099ff")
                .set("font-size", "16px")
                .set("font-weight", "800")
                .set("border-radius", "8px")
                .set("border", "2px solid #0099ff");

        loginSwitchButton.addClickListener(event -> switchToLogin());

        buttonContainer.add(registerSwitchButton, loginSwitchButton);

        // 默认隐藏登录按钮（初始状态是登录模式）
        loginSwitchButton.getStyle().set("display", "none");

        welcomeContainer.add(welcomeTitle, welcomeDesc, icon, buttonContainer);
    }

    private void updateLayout() {
        leftPanel.removeAll();
        rightPanel.removeAll();

        if (isLoginMode) {
            // 登录模式：左侧登录表单，右侧欢迎信息
            leftPanel.add(formContainer);
            rightPanel.add(welcomeContainer);
            
            // 显示注册切换按钮，隐藏登录切换按钮
            welcomeContainer.getChildren().forEach(child -> {
                if (child instanceof Div buttonContainer) {
                    buttonContainer.getChildren().forEach(btn -> {
                        if (btn instanceof Button) {
                            Button button = (Button) btn;
                            if (button.getText().equals("Create Account")) {
                                button.getStyle().set("display", "block");
                            } else {
                                button.getStyle().set("display", "none");
                            }
                        }
                    });
                }
            });

            // 显示登录表单，隐藏注册表单
            formContainer.getChildren().forEach(child -> {
                if (child instanceof Div form) {
                    if (form.getChildren().findFirst().isPresent() && 
                        form.getChildren().findFirst().get() instanceof Span title &&
                        title.getText().equals("Sign In")) {
                        form.getStyle().set("display", "block");
                    } else {
                        form.getStyle().set("display", "none");
                    }
                }
            });
        } else {
            // 注册模式：左侧欢迎信息，右侧注册表单
            leftPanel.add(welcomeContainer);
            rightPanel.add(formContainer);
            
            // 显示登录切换按钮，隐藏注册切换按钮
            welcomeContainer.getChildren().forEach(child -> {
                if (child instanceof Div buttonContainer) {
                    buttonContainer.getChildren().forEach(btn -> {
                        if (btn instanceof Button) {
                            Button button = (Button) btn;
                            if (button.getText().equals("Sign In")) {
                                button.getStyle().set("display", "block");
                            } else {
                                button.getStyle().set("display", "none");
                            }
                        }
                    });
                }
            });

            // 显示注册表单，隐藏登录表单
            formContainer.getChildren().forEach(child -> {
                if (child instanceof Div form) {
                    if (form.getChildren().findFirst().isPresent() && 
                        form.getChildren().findFirst().get() instanceof Span title &&
                        title.getText().equals("Create Account")) {
                        form.getStyle().set("display", "block");
                    } else {
                        form.getStyle().set("display", "none");
                    }
                }
            });
        }
    }

    private void switchToRegister() {
        if (isAnimating) return;
        isAnimating = true;
        isLoginMode = false;
        
        UI.getCurrent().getPage().executeJs(
                "var container = $0;" +
                "var leftPanel = container.children[0];" +
                "var rightPanel = container.children[1];" +
                "leftPanel.style.transition = 'all 0.5s cubic-bezier(0.4, 0, 0.2, 1)';" +
                "rightPanel.style.transition = 'all 0.5s cubic-bezier(0.4, 0, 0.2, 1)';" +
                "leftPanel.style.opacity = '0';" +
                "leftPanel.style.transform = 'translateX(-20px)';" +
                "rightPanel.style.opacity = '0';" +
                "rightPanel.style.transform = 'translateX(20px)';" +
                "setTimeout(() => {" +
                "  $1.$server.updateLayoutSync();" +
                "}, 500);",
                container.getElement(),
                getElement()
        );
    }

    private void switchToLogin() {
        if (isAnimating) return;
        isAnimating = true;
        isLoginMode = true;
        
        UI.getCurrent().getPage().executeJs(
                "var container = $0;" +
                "var leftPanel = container.children[0];" +
                "var rightPanel = container.children[1];" +
                "leftPanel.style.transition = 'all 0.5s cubic-bezier(0.4, 0, 0.2, 1)';" +
                "rightPanel.style.transition = 'all 0.5s cubic-bezier(0.4, 0, 0.2, 1)';" +
                "leftPanel.style.opacity = '0';" +
                "leftPanel.style.transform = 'translateX(20px)';" +
                "rightPanel.style.opacity = '0';" +
                "rightPanel.style.transform = 'translateX(-20px)';" +
                "setTimeout(() => {" +
                "  $1.$server.updateLayoutSync();" +
                "}, 500);",
                container.getElement(),
                getElement()
        );
    }

    @ClientCallable
    public void updateLayoutSync() {
        updateLayout();
        
        UI.getCurrent().getPage().executeJs(
                "var container = $0;" +
                "var leftPanel = container.children[0];" +
                "var rightPanel = container.children[1];" +
                "leftPanel.style.opacity = '1';" +
                "leftPanel.style.transform = 'translateX(0)';" +
                "rightPanel.style.opacity = '1';" +
                "rightPanel.style.transform = 'translateX(0)';" +
                "setTimeout(() => {" +
                "  $1.$server.animationComplete();" +
                "}, 600);",
                container.getElement(),
                getElement()
        );
    }

    @ClientCallable
    public void animationComplete() {
        isAnimating = false;
    }

    private void handleLogin() {
        String username = loginUsernameField.getValue();
        String password = loginPasswordField.getValue();

        if (username.isBlank() || password.isBlank()) {
            Notification.show("Please enter username and password", 3000, Notification.Position.TOP_CENTER);
            return;
        }

        if (loginService.login(username, password)) {
            Notification.show("Login successful!", 2000, Notification.Position.TOP_CENTER);
            String targetUrl = redirectUrl != null && !redirectUrl.isEmpty() ? redirectUrl : "";
            UI.getCurrent().navigate(targetUrl);
        } else {
            Notification.show("Invalid username or password", 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void handleRegister() {
        String username = registerUsernameField.getValue();
        String email = registerEmailField.getValue();
        String password = registerPasswordField.getValue();
        UserRole role = roleComboBox.getValue();

        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            Notification.show("Please fill in all fields", 3000, Notification.Position.TOP_CENTER);
            return;
        }

        if (password.length() < 6) {
            Notification.show("Password must be at least 6 characters", 3000, Notification.Position.TOP_CENTER);
            return;
        }

        // 注册时使用 username 作为 fullName（因为去掉了 fullName 字段）
        if (loginService.register(username, password, email, username, role)) {
            Notification.show("Registration successful! Please login.", 3000, Notification.Position.TOP_CENTER);
            // 注册成功后切换回登录模式
            switchToLogin();
            // 清空表单
            registerUsernameField.clear();
            registerEmailField.clear();
            registerPasswordField.clear();
            roleComboBox.setValue(UserRole.BOOKING_STAFF);
        } else {
            Notification.show("Username or email already exists", 3000, Notification.Position.TOP_CENTER);
        }
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
        if (parameter != null && parameter.equals("register")) {
            // 如果参数是register，直接切换到注册模式
            isLoginMode = false;
            updateLayout();
        } else {
            this.redirectUrl = parameter;
        }
    }

    public static void openLoginWithRedirect(String redirectUrl) {
        VaadinSession.getCurrent().setAttribute("redirectAfterLogin", redirectUrl);
        UI.getCurrent().navigate("login/" + (redirectUrl != null ? redirectUrl : ""));
    }
}
