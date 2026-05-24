package com.eduaccess.ui;

import com.eduaccess.domain.Cinema;
import com.eduaccess.repository.CinemaRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;

@Route(value = "home", layout = MainLayout.class)
@PageTitle("HCBS — Home")
public class HomeView extends VerticalLayout {

    private final CinemaRepository cinemaRepository;

    public HomeView(CinemaRepository cinemaRepository) {
        this.cinemaRepository = cinemaRepository;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        addClassNames(LumoUtility.Padding.LARGE);

        H2 headline = new H2("Horizon Cinemas Booking System");
        headline.addClassNames(
                LumoUtility.FontSize.XXLARGE,
                LumoUtility.Margin.Bottom.SMALL
        );

        Paragraph description = new Paragraph(
                "Welcome to the Horizon Cinemas Booking System. " +
                        "Select a city and cinema to view available film screenings, " +
                        "check seat availability, and create customer bookings."
        );
        description.addClassNames(
                LumoUtility.TextColor.SECONDARY,
                LumoUtility.MaxWidth.SCREEN_MEDIUM
        );
        description.getStyle().set("text-align", "center");

        List<Cinema> cinemas = cinemaRepository.findAll();
        if (cinemas == null) {
            cinemas = List.of();
        }

        List<String> cities = cinemas.stream()
                .map(Cinema::getCity)
                .filter(city -> city != null && !city.isBlank())
                .distinct()
                .sorted()
                .toList();

        ComboBox<String> cityBox = new ComboBox<>("Select city");
        cityBox.setWidth("320px");
        cityBox.setPlaceholder("Choose a city...");
        cityBox.setItems(cities);

        ComboBox<Cinema> cinemaBox = new ComboBox<>("Select cinema");
        cinemaBox.setWidth("320px");
        cinemaBox.setPlaceholder("Choose a cinema...");
        cinemaBox.setItemLabelGenerator(Cinema::toString);
        cinemaBox.setEnabled(false);

        cityBox.addValueChangeListener(event -> {
            String selectedCity = event.getValue();

            cinemaBox.clear();

            if (selectedCity == null || selectedCity.isBlank()) {
                cinemaBox.setEnabled(false);
                return;
            }

            List<Cinema> cinemasInCity =
                    cinemaRepository.findByCityIgnoreCaseOrderByNameAsc(selectedCity);

            cinemaBox.setItems(cinemasInCity);
            cinemaBox.setEnabled(true);
        });

        Button continueButton = new Button("Continue");
        continueButton.addClickListener(event -> {
            Cinema selectedCinema = cinemaBox.getValue();

            if (selectedCinema == null) {
                Notification.show("Please select a cinema first.");
                return;
            }
            getUI().ifPresent(ui -> ui.navigate(FilmListingView.class));
        });

        HorizontalLayout selectorRow = new HorizontalLayout(cityBox, cinemaBox, continueButton);
        selectorRow.setAlignItems(Alignment.END);
        selectorRow.setJustifyContentMode(JustifyContentMode.CENTER);
        selectorRow.setSpacing(true);
        selectorRow.getStyle().set("flex-wrap", "wrap");

        HorizontalLayout statsRow = new HorizontalLayout(
                statCard(String.valueOf(cinemaRepository.count()), "Cinemas"),
                statCard(String.valueOf(cities.size()), "Cities"),
                statCard("3", "Core Functions")
        );
        statsRow.setSpacing(true);
        statsRow.setJustifyContentMode(JustifyContentMode.CENTER);
        statsRow.getStyle().set("flex-wrap", "wrap");
        statsRow.addClassNames(LumoUtility.Margin.Top.LARGE);

        add(headline, description, selectorRow, statsRow);
    }

    private Div statCard(String number, String label) {
        Div card = new Div();
        card.addClassNames(
                LumoUtility.Background.CONTRAST_5,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.Padding.MEDIUM
        );

        card.getStyle()
                .set("text-align", "center")
                .set("min-width", "120px");

        H3 num = new H3(number);
        num.addClassNames(
                LumoUtility.TextColor.PRIMARY,
                LumoUtility.Margin.NONE
        );

        Span lbl = new Span(label);
        lbl.addClassNames(
                LumoUtility.FontSize.SMALL,
                LumoUtility.TextColor.SECONDARY
        );

        card.add(num, lbl);
        return card;
    }
}