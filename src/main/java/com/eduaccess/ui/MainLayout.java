package com.eduaccess.ui;

import com.eduaccess.domain.UserAccount;
import com.eduaccess.domain.UserRole;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainLayout extends AppLayout {

    private static final String DARK_BG = "#020b1d";
    private static final String BLUE = "#0072ce";
    private static final String BORDER = "rgba(255,255,255,0.18)";
    private static final String SOFT_PANEL = "rgba(255,255,255,0.035)";

    private final CinemaRepository cinemaRepository;
    private final LoginService loginService;
    private Div authSection;
    private Div navSection;
    private Div header;

    public MainLayout(CinemaRepository cinemaRepository, LoginService loginService) {
        this.cinemaRepository = cinemaRepository;
        this.loginService = loginService;
        createHeader();
    }

    private void createHeader() {
        header = new Div();
        header.setWidthFull();
        header.getStyle()
                .set("height", "78px")
                .set("width", "100%")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("background", DARK_BG)
                .set("color", "white")
                .set("border-bottom", "1px solid rgba(255,255,255,0.08)")
                .set("box-sizing", "border-box")
                .set("position", "relative")
                .set("z-index", "10");

        Div inner = new Div();
        inner.setWidthFull();
        inner.getStyle()
                .set("max-width", "1520px")
                .set("height", "100%")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("padding", "0 28px")
                .set("box-sizing", "border-box")
                .set("gap", "18px");

        Div left = new Div();
        left.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "28px")
                .set("min-width", "0");

        RouterLink logo = new RouterLink("Group5", FilmListingView.class);
        logo.getStyle()
                .set("font-size", "34px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.16em")
                .set("color", "white")
                .set("line-height", "1")
                .set("text-decoration", "none")
                .set("white-space", "nowrap");

        navSection = createNav();
        left.add(logo, navSection);

        Div right = new Div();
        right.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "12px")
                .set("flex-shrink", "0");

        Button locationButton = iconButton("map-marker", "Choose city");
        locationButton.addClickListener(event -> openCityDialog());

        Button searchButton = iconButton("search", "Search films or cinemas");
        searchButton.addClickListener(event -> openSearchDialog());

        authSection = new Div();
        authSection.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "12px");
        updateAuthSection();

        right.add(locationButton, searchButton, authSection);

        inner.add(left, right);
        header.add(inner);

        addToNavbar(header);
    }

    private void recreateHeader() {
        getElement().removeAllChildren();
        createHeader();
    }

    private Div createNav() {
        Div nav = new Div();
        nav.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "12px")
                .set("flex-wrap", "nowrap");

        nav.add(iconNavLink("film", "Films", FilmListingView.class));
        nav.add(iconNavLink("ticket", "Booking", BookingView.class));
        nav.add(iconNavLink("close-circle", "Cancellation", CancellationView.class));

        UserAccount currentUser = loginService.getCurrentUser();
        if (currentUser != null) {
            // 第4个：Food Orders - 所有登录用户可见
            nav.add(iconNavLink("cutlery", "Food Orders", FoodOrdersView.class));

            UserRole role = currentUser.getRole();
            
            // BOOKING_STAFF, ADMIN, MANAGER 都能看到 Refund History 和 Email Management
            if (role == UserRole.BOOKING_STAFF || role == UserRole.ADMIN || role == UserRole.MANAGER) {
                // 第5个：Refund History
                nav.add(iconNavLink("archive", "Refund History", CancellationHistoryView.class));
                // 第6个：Email Management
                nav.add(iconNavLink("envelope-o", "Email Management", EmailManagementView.class));
            }

            // ADMIN 和 MANAGER 能看到 Admin Schedule 和 Audit Log
            if (role == UserRole.ADMIN || role == UserRole.MANAGER) {
                // 第7个：Admin Schedule
                nav.add(iconNavLink("calendar-clock", "Admin Schedule", AdminScheduleView.class));
                // 第8个：Audit Log
                nav.add(iconNavLink("clipboard-check", "Audit Log", AuditLogView.class));
            }

            // MANAGER 能看到 Manager Dashboard, Manager Cinemas 和 Films Mgmt
            if (role == UserRole.MANAGER) {
                // 第9个：Manager Dashboard
                nav.add(iconNavLink("bar-chart", "Manager Dashboard", ManagerDashboardView.class));
                // 第10个：Manager Cinemas
                nav.add(iconNavLink("building", "Manager Cinemas", ManagerCinemaView.class));
                // 第11个：Films Mgmt
                nav.add(iconNavLink("records", "Films Mgmt", ManagerListingsView.class));
            }
        }

        return nav;
    }

    private RouterLink iconNavLink(String iconName, String label, Class<? extends Component> target) {
        RouterLink link = new RouterLink();
        link.setRoute(target);
        link.getElement().setProperty("title", label);
        link.getElement().setAttribute("aria-label", label);
        applyIconControlStyle(link);

        Icon icon = vaadinIcon(iconName);
        link.add(icon);

        return link;
    }

    private Button iconButton(String iconName, String label) {
        Button button = new Button(vaadinIcon(iconName));
        button.getElement().setProperty("title", label);
        button.getElement().setAttribute("aria-label", label);
        applyIconControlStyle(button);
        return button;
    }

    private Icon vaadinIcon(String iconName) {
        Icon icon = new Icon("vaadin", iconName);
        icon.setSize("21px");
        icon.getStyle()
                .set("color", "#e5e7eb")
                .set("line-height", "1");
        return icon;
    }

    private void applyIconControlStyle(Component component) {
        component.getElement().getStyle()
                .set("width", "50px")
                .set("height", "44px")
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("padding", "0")
                .set("margin", "0")
                .set("border-radius", "999px")
                .set("background", SOFT_PANEL)
                .set("border", "1px solid " + BORDER)
                .set("box-shadow", "0 8px 22px rgba(0,0,0,0.20)")
                .set("color", "white")
                .set("text-decoration", "none")
                .set("cursor", "pointer")
                .set("box-sizing", "border-box")
                .set("transition", "transform 0.16s ease, border-color 0.16s ease, background 0.16s ease");
    }

    private void updateAuthSection() {
        authSection.removeAll();

        UserAccount currentUser = loginService.getCurrentUser();

        if (currentUser != null) {
            Span userInfo = new Span(currentUser.getFullName() + " (" +
                    currentUser.getRole().name().replace("_", " ") + ")");
            userInfo.getStyle()
                    .set("font-size", "14px")
                    .set("color", "#dbeafe")
                    .set("font-weight", "750")
                    .set("white-space", "nowrap");

            Button logoutButton = new Button("Logout");
            logoutButton.getStyle()
                    .set("height", "44px")
                    .set("padding", "0 22px")
                    .set("background", "#dc2626")
                    .set("color", "white")
                    .set("font-weight", "850")
                    .set("border-radius", "999px")
                    .set("border", "1px solid rgba(255,255,255,0.10)")
                    .set("font-size", "15px")
                    .set("letter-spacing", "0.03em")
                    .set("box-shadow", "0 10px 24px rgba(220,38,38,0.22)");

            logoutButton.addClickListener(event -> {
                loginService.logout();
                recreateHeader();
                UI.getCurrent().navigate("");
            });

            authSection.add(userInfo, logoutButton);
        } else {
            RouterLink loginLink = new RouterLink("Login", LoginView.class);
            loginLink.getStyle()
                    .set("height", "44px")
                    .set("display", "inline-flex")
                    .set("align-items", "center")
                    .set("padding", "0 20px")
                    .set("border-radius", "999px")
                    .set("border", "1px solid " + BORDER)
                    .set("background", SOFT_PANEL)
                    .set("color", "white")
                    .set("text-decoration", "none")
                    .set("font-weight", "850")
                    .set("font-size", "15px");

            Button registerButton = new Button("Register");
            registerButton.getStyle()
                    .set("height", "44px")
                    .set("padding", "0 20px")
                    .set("background", BLUE)
                    .set("color", "white")
                    .set("font-weight", "850")
                    .set("border-radius", "999px")
                    .set("border", "1px solid rgba(255,255,255,0.10)")
                    .set("font-size", "15px");

            registerButton.addClickListener(event -> UI.getCurrent().navigate("login/register"));

            authSection.add(loginLink, registerButton);
        }
    }

    private void openCityDialog() {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);

        Div content = new Div();
        content.getStyle()
                .set("width", "420px")
                .set("padding", "8px")
                .set("box-sizing", "border-box");

        Span title = new Span("Choose your city");
        title.getStyle()
                .set("display", "block")
                .set("font-size", "24px")
                .set("font-weight", "900")
                .set("margin-bottom", "18px");

        List<String> cities = cinemaRepository.findAll()
                .stream()
                .map(cinema -> cinema.getCity())
                .filter(city -> city != null && !city.isBlank())
                .distinct()
                .sorted()
                .toList();

        ComboBox<String> cityBox = new ComboBox<>("City");
        cityBox.setWidthFull();
        cityBox.setPlaceholder("Select city");
        cityBox.setClearButtonVisible(true);
        cityBox.setItems(cities);

        String selectedCity = getSelectedCity();
        if (selectedCity != null && cities.contains(selectedCity)) {
            cityBox.setValue(selectedCity);
        }

        Button applyButton = new Button("Apply", event -> {
            String city = cityBox.getValue();

            VaadinSession.getCurrent().setAttribute(
                    "selectedCity",
                    city == null ? "" : city
            );

            dialog.close();
            UI.getCurrent().getPage().setLocation(buildFilmsUrl(null, city));
        });

        applyButton.getStyle()
                .set("margin-top", "18px")
                .set("height", "44px")
                .set("width", "130px")
                .set("background", BLUE)
                .set("color", "white")
                .set("font-weight", "850")
                .set("border-radius", "999px");

        content.add(title, cityBox, applyButton);
        dialog.add(content);
        dialog.open();
    }

    private void openSearchDialog() {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);

        Div content = new Div();
        content.getStyle()
                .set("width", "460px")
                .set("padding", "8px")
                .set("box-sizing", "border-box");

        Span title = new Span("Search films or cinemas");
        title.getStyle()
                .set("display", "block")
                .set("font-size", "24px")
                .set("font-weight", "900")
                .set("margin-bottom", "18px");

        TextField searchField = new TextField("Keyword");
        searchField.setWidthFull();
        searchField.setPlaceholder("e.g. Dune, London, Science Fiction");

        Button searchButton = new Button("Search", event -> {
            String keyword = searchField.getValue();
            String city = getSelectedCity();

            dialog.close();
            UI.getCurrent().getPage().setLocation(buildFilmsUrl(keyword, city));
        });

        searchButton.getStyle()
                .set("margin-top", "18px")
                .set("height", "44px")
                .set("width", "130px")
                .set("background", BLUE)
                .set("color", "white")
                .set("font-weight", "850")
                .set("border-radius", "999px");

        content.add(title, searchField, searchButton);
        dialog.add(content);
        dialog.open();
    }

    private String buildFilmsUrl(String keyword, String city) {
        StringBuilder url = new StringBuilder("/");

        boolean hasQuery = false;

        if (keyword != null && !keyword.isBlank()) {
            url.append("?q=")
                    .append(URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8));
            hasQuery = true;
        }

        if (city != null && !city.isBlank()) {
            url.append(hasQuery ? "&" : "?")
                    .append("city=")
                    .append(URLEncoder.encode(city.trim(), StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    private String getSelectedCity() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return "";
        }

        Object selectedCity = session.getAttribute("selectedCity");
        return selectedCity == null ? "" : selectedCity.toString();
    }
}
