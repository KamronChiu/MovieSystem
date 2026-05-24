package com.eduaccess.ui;

import com.eduaccess.repository.CinemaRepository;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainLayout extends AppLayout {

    private final CinemaRepository cinemaRepository;

    public MainLayout(CinemaRepository cinemaRepository) {
        this.cinemaRepository = cinemaRepository;
        createHeader();
    }

    private void createHeader() {
        Div header = new Div();
        header.setWidthFull();
        header.getStyle()
                .set("height", "76px")
                .set("width", "100%")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("background", "#020b1d")
                .set("color", "white")
                .set("border-bottom", "1px solid rgba(255,255,255,0.08)")
                .set("box-sizing", "border-box");

        Div inner = new Div();
        inner.setWidthFull();
        inner.getStyle()
                .set("max-width", "1320px")
                .set("height", "100%")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("padding", "0 48px")
                .set("box-sizing", "border-box");

        Div left = new Div();
        left.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "36px");

        Span logo = new Span("HCBS");
        logo.getStyle()
                .set("font-size", "34px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.20em")
                .set("color", "white")
                .set("line-height", "1");

        Div nav = new Div();
        nav.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "32px");

        nav.add(
                navLink("Films", FilmListingView.class),
                navLink("Booking", BookingView.class),
                navLink("Cancellation", CancellationView.class),
                navLink("Admin", AdminScheduleView.class),
                navLink("Manager", ManagerCinemaView.class),
                navLink("Home", HomeView.class)
        );

        left.add(logo, nav);

        Div right = new Div();
        right.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "18px");

        Button locationButton = iconButton("⌖");
        locationButton.getElement().setProperty("title", "Choose city");
        locationButton.addClickListener(event -> openCityDialog());

        Span cityLabel = new Span(getSelectedCityLabel());
        cityLabel.getStyle()
                .set("font-size", "15px")
                .set("font-weight", "700")
                .set("color", "#dbeafe");

        Button searchButton = iconButton("⌕");
        searchButton.getElement().setProperty("title", "Search films or cinemas");
        searchButton.addClickListener(event -> openSearchDialog());

        right.add(locationButton, cityLabel, searchButton);

        inner.add(left, right);
        header.add(inner);

        addToNavbar(header);
    }

    private RouterLink navLink(String text, Class<? extends Component> target) {
        RouterLink link = new RouterLink(text, target);

        link.getStyle()
                .set("color", "white")
                .set("text-decoration", "none")
                .set("font-size", "17px")
                .set("font-weight", "700")
                .set("letter-spacing", "0.02em")
                .set("opacity", "0.94");

        return link;
    }

    private Button iconButton(String text) {
        Button button = new Button(text);

        button.getStyle()
                .set("width", "42px")
                .set("height", "42px")
                .set("border-radius", "50%")
                .set("background", "transparent")
                .set("color", "white")
                .set("border", "1px solid rgba(255,255,255,0.35)")
                .set("font-size", "26px")
                .set("font-weight", "500")
                .set("padding", "0")
                .set("cursor", "pointer");

        return button;
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
                .set("font-weight", "800")
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
                .set("height", "42px")
                .set("width", "120px")
                .set("background", "#0072ce")
                .set("color", "white")
                .set("font-weight", "800")
                .set("border-radius", "0");

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
                .set("font-weight", "800")
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
                .set("height", "42px")
                .set("width", "120px")
                .set("background", "#0072ce")
                .set("color", "white")
                .set("font-weight", "800")
                .set("border-radius", "0");

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
        return (String) session.getAttribute("selectedCity");
    }

    private String getSelectedCityLabel() {
        String city = getSelectedCity();
        return (city == null || city.isBlank()) ? "Choose city" : city;
    }
}