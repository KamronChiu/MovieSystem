package com.eduaccess.ui;

import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.Film;
import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.repository.ScreenRepository;
import com.eduaccess.service.SchedulingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Route(value = "admin/screenings", layout = MainLayout.class)
@PageTitle("HCBS — Admin Screenings")
public class AdminScreeningView extends Div {

    private final SchedulingService schedulingService;
    private final FilmRepository filmRepository;
    private final CinemaRepository cinemaRepository;
    private final ScreenRepository screenRepository;

    private final Grid<Screening> grid = new Grid<>(Screening.class, false);

    private final ComboBox<Film> filmBox = new ComboBox<>("Film");
    private final ComboBox<Cinema> cinemaBox = new ComboBox<>("Cinema");
    private final ComboBox<Screen> screenBox = new ComboBox<>("Screen");
    private final DatePicker datePicker = new DatePicker("Date");
    private final TimePicker startTimePicker = new TimePicker("Start time");
    private final ComboBox<ScreeningType> screeningTypeBox = new ComboBox<>("Screening type");

    private Screening selectedScreening;

    public AdminScreeningView(
            SchedulingService schedulingService,
            FilmRepository filmRepository,
            CinemaRepository cinemaRepository,
            ScreenRepository screenRepository
    ) {
        this.schedulingService = schedulingService;
        this.filmRepository = filmRepository;
        this.cinemaRepository = cinemaRepository;
        this.screenRepository = screenRepository;

        setWidthFull();
        getStyle()
                .set("min-height", "100vh")
                .set("background", "#020b1d")
                .set("color", "white")
                .set("padding", "44px")
                .set("box-sizing", "border-box");

        Div page = new Div();
        page.getStyle()
                .set("max-width", "1320px")
                .set("margin", "0 auto");

        H1 title = new H1("Admin Screening Management");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "36px")
                .set("font-weight", "900")
                .set("color", "white");

        Paragraph description = new Paragraph(
                "Add, update and remove film screenings. The system checks screen conflicts, show limits and booking constraints."
        );
        description.getStyle()
                .set("color", "rgba(255,255,255,0.82)")
                .set("font-size", "16px")
                .set("margin", "10px 0 28px 0");

        configureForm();
        configureGrid();

        page.add(title, description, buildFormCard(), buildGridCard());
        add(page);

        refreshGrid();
    }

    private void configureForm() {
        filmBox.setItems(filmRepository.findAll());
        filmBox.setItemLabelGenerator(Film::getTitle);
        filmBox.setPlaceholder("Select film");
        filmBox.setWidth("300px");

        cinemaBox.setItems(cinemaRepository.findAll());
        cinemaBox.setItemLabelGenerator(cinema -> cinema.getCity() + " - " + cinema.getName());
        cinemaBox.setPlaceholder("Select cinema");
        cinemaBox.setWidth("300px");

        screenBox.setPlaceholder("Select screen");
        screenBox.setItemLabelGenerator(this::formatScreenLabel);
        screenBox.setWidth("240px");

        cinemaBox.addValueChangeListener(event -> {
            Cinema selectedCinema = event.getValue();
            screenBox.clear();

            if (selectedCinema == null) {
                screenBox.setItems(List.of());
                return;
            }

            List<Screen> screens = screenRepository.findAll()
                    .stream()
                    .filter(screen -> screen.getCinema().getId().equals(selectedCinema.getId()))
                    .sorted(Comparator.comparingInt(Screen::getScreenNumber))
                    .toList();

            screenBox.setItems(screens);
        });

        datePicker.setValue(LocalDate.now().plusDays(1));
        datePicker.setWidth("180px");

        startTimePicker.setValue(LocalTime.of(12, 0));
        startTimePicker.setStep(java.time.Duration.ofMinutes(10));
        startTimePicker.setWidth("180px");

        screeningTypeBox.setItems(ScreeningType.values());
        screeningTypeBox.setValue(ScreeningType.REGULAR);
        screeningTypeBox.setItemLabelGenerator(this::formatScreeningTypeLabel);
        screeningTypeBox.setWidth("220px");

        styleDarkField(filmBox);
        styleDarkField(cinemaBox);
        styleDarkField(screenBox);
        styleDarkField(datePicker);
        styleDarkField(startTimePicker);
        styleDarkField(screeningTypeBox);
    }

    private void configureGrid() {
        grid.addColumn(screening -> screening.getFilm().getTitle())
                .setHeader("Film")
                .setAutoWidth(true)
                .setSortable(true);

        grid.addColumn(screening -> screening.getScreen().getCinema().getCity())
                .setHeader("City")
                .setAutoWidth(true)
                .setSortable(true);

        grid.addColumn(screening -> screening.getScreen().getCinema().getName())
                .setHeader("Cinema")
                .setAutoWidth(true)
                .setSortable(true);

        grid.addColumn(screening -> "Screen " + screening.getScreen().getScreenNumber())
                .setHeader("Screen")
                .setAutoWidth(true);

        grid.addColumn(Screening::getScreeningDate)
                .setHeader("Date")
                .setAutoWidth(true)
                .setSortable(true);

        grid.addColumn(Screening::getStartTime)
                .setHeader("Start")
                .setAutoWidth(true);

        grid.addColumn(Screening::getEndTime)
                .setHeader("End")
                .setAutoWidth(true);

        grid.addColumn(screening -> formatScreeningTypeLabel(screening.getScreeningType()))
                .setHeader("Type")
                .setAutoWidth(true)
                .setSortable(true);

        grid.addComponentColumn(screening -> {
            Button editButton = new Button("Edit");
            editButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            editButton.addClickListener(event -> loadScreeningIntoForm(screening));
            return editButton;
        }).setHeader("Edit");

        grid.setWidthFull();
        grid.setHeight("520px");
        grid.getStyle()
                .set("background", "white")
                .set("border-radius", "14px")
                .set("overflow", "hidden");
    }

    private Div buildFormCard() {
        Div card = darkCard();

        Div fields = new Div();
        fields.getStyle()
                .set("display", "flex")
                .set("gap", "14px")
                .set("flex-wrap", "wrap")
                .set("align-items", "end");

        Button addButton = primaryButton("Add screening");
        addButton.addClickListener(event -> createScreening());

        Button updateButton = secondaryButton("Update selected");
        updateButton.addClickListener(event -> updateSelectedScreening());

        Button deleteButton = dangerButton("Delete selected");
        deleteButton.addClickListener(event -> deleteSelectedScreening());

        Button clearButton = secondaryButton("Clear form");
        clearButton.addClickListener(event -> clearForm());

        Button cleanupButton = secondaryButton("Clean expired");
        cleanupButton.addClickListener(event -> cleanupExpiredScreenings());

        fields.add(
                filmBox,
                cinemaBox,
                screenBox,
                datePicker,
                startTimePicker,
                screeningTypeBox,
                addButton,
                updateButton,
                deleteButton,
                clearButton,
                cleanupButton
        );

        card.add(fields);
        return card;
    }

    private Div buildGridCard() {
        Div card = darkCard();
        card.add(grid);
        return card;
    }

    private void createScreening() {
        try {
            validateForm();

            schedulingService.createScreening(
                    filmBox.getValue().getId(),
                    screenBox.getValue().getId(),
                    datePicker.getValue(),
                    startTimePicker.getValue(),
                    screeningTypeBox.getValue()
            );

            Notification.show("Screening added successfully.");
            refreshGrid();
            clearForm();

        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void updateSelectedScreening() {
        if (selectedScreening == null) {
            Notification.show("Select a screening first.");
            return;
        }

        try {
            validateForm();

            schedulingService.updateScreening(
                    selectedScreening.getId(),
                    filmBox.getValue().getId(),
                    screenBox.getValue().getId(),
                    datePicker.getValue(),
                    startTimePicker.getValue(),
                    screeningTypeBox.getValue()
            );

            Notification.show("Screening updated successfully.");
            refreshGrid();
            clearForm();

        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void deleteSelectedScreening() {
        if (selectedScreening == null) {
            Notification.show("Select a screening first.");
            return;
        }

        try {
            schedulingService.deleteScreening(selectedScreening.getId());

            Notification.show("Screening deleted successfully.");
            refreshGrid();
            clearForm();

        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void cleanupExpiredScreenings() {
        try {
            int deleted = schedulingService.cleanupExpiredUnbookedScreenings();
            Notification.show("Expired unbooked screenings removed: " + deleted);
            refreshGrid();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void loadScreeningIntoForm(Screening screening) {
        selectedScreening = screening;

        filmBox.setValue(screening.getFilm());
        cinemaBox.setValue(screening.getScreen().getCinema());
        screenBox.setItems(
                screenRepository.findAll()
                        .stream()
                        .filter(screen -> screen.getCinema().getId().equals(screening.getScreen().getCinema().getId()))
                        .sorted(Comparator.comparingInt(Screen::getScreenNumber))
                        .toList()
        );
        screenBox.setValue(screening.getScreen());
        datePicker.setValue(screening.getScreeningDate());
        startTimePicker.setValue(screening.getStartTime());
        screeningTypeBox.setValue(screening.getScreeningType());

        Notification.show("Screening selected for editing.");
    }

    private void validateForm() {
        if (filmBox.getValue() == null) {
            throw new IllegalArgumentException("Please select a film.");
        }

        if (cinemaBox.getValue() == null) {
            throw new IllegalArgumentException("Please select a cinema.");
        }

        if (screenBox.getValue() == null) {
            throw new IllegalArgumentException("Please select a screen.");
        }

        if (datePicker.getValue() == null) {
            throw new IllegalArgumentException("Please select a date.");
        }

        if (startTimePicker.getValue() == null) {
            throw new IllegalArgumentException("Please select a start time.");
        }

        if (screeningTypeBox.getValue() == null) {
            throw new IllegalArgumentException("Please select a screening type.");
        }
    }

    private void refreshGrid() {
        List<Screening> screenings = schedulingService.findAllScreeningsForAdmin();
        grid.setItems(screenings);
    }

    private void clearForm() {
        selectedScreening = null;
        filmBox.clear();
        cinemaBox.clear();
        screenBox.clear();
        datePicker.setValue(LocalDate.now().plusDays(1));
        startTimePicker.setValue(LocalTime.of(12, 0));
        screeningTypeBox.setValue(ScreeningType.REGULAR);
    }


    private String formatScreeningTypeLabel(ScreeningType type) {
        if (type == null || type == ScreeningType.REGULAR) {
            return "Regular";
        }

        return "Advance Preview";
    }

    private String formatScreenLabel(Screen screen) {
        if (screen == null) {
            return "";
        }

        return "Screen " + screen.getScreenNumber() + " - " + screen.getCapacity() + " seats";
    }

    private void styleDarkField(com.vaadin.flow.component.Component component) {
        component.getElement().getStyle()
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.08)")
                .set("--vaadin-input-field-value-color", "white")
                .set("--vaadin-input-field-label-color", "white")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.70)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.25)")
                .set("--vaadin-input-field-hover-highlight", "rgba(255,255,255,0.08)")
                .set("--vaadin-input-field-focused-highlight", "#38bdf8")
                .set("color", "white");
    }

    private Div darkCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", "rgba(255,255,255,0.06)")
                .set("border", "1px solid rgba(255,255,255,0.14)")
                .set("border-radius", "18px")
                .set("padding", "22px")
                .set("box-sizing", "border-box")
                .set("margin-bottom", "24px");

        return card;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.getStyle()
                .set("height", "40px")
                .set("background", "#0072ce")
                .set("border-radius", "0")
                .set("font-weight", "800");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyle()
                .set("height", "40px")
                .set("background", "transparent")
                .set("color", "white")
                .set("border", "1px solid rgba(255,255,255,0.45)")
                .set("border-radius", "0")
                .set("font-weight", "700");
        return button;
    }

    private Button dangerButton(String text) {
        Button button = new Button(text);
        button.getStyle()
                .set("height", "40px")
                .set("background", "#991b1b")
                .set("color", "white")
                .set("border", "1px solid #ef4444")
                .set("border-radius", "0")
                .set("font-weight", "800");
        return button;
    }
}