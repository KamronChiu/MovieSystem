package com.eduaccess.ui;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.Film;
import com.eduaccess.domain.HallType;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.domain.Seat;
import com.eduaccess.domain.SeatType;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.service.BookingService;
import com.eduaccess.service.LoginService;
import com.eduaccess.service.PricingService;
import com.eduaccess.service.ScreeningService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "booking", layout = MainLayout.class)
@PageTitle("HCBS — Booking")
public class BookingView extends Div implements HasUrlParameter<Long>, BeforeEnterObserver {

    private final LoginService loginService;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkBookingAccess(event, loginService);
    }

    private static final String DARK_BG = "#020b1d";
    private static final String BLUE = "#0072ce";
    private static final String LIGHT_BG = "#f4f7fb";
    private static final String LIGHT_PANEL = "#ffffff";
    private static final String LIGHT_PANEL_SOFT = "#eef4fb";
    private static final String LIGHT_TEXT = "#142033";
    private static final String LIGHT_MUTED = "#64748b";
    private static final String LIGHT_BORDER = "#d8e2ef";

    private enum BookingStep {
        SEATS,
        TICKETS,
        SUMMARY,
        PAYMENT
    }

    private final CinemaRepository cinemaRepository;
    private final ScreeningService screeningService;
    private final BookingService bookingService;
    private final PricingService pricingService;

    private final ComboBox<Film> filmBox = new ComboBox<>();
    private final ComboBox<Cinema> cinemaBox = new ComboBox<>();
    private final ComboBox<HallType> hallTypeBox = new ComboBox<>();
    private final ComboBox<String> formatBox = new ComboBox<>(); // 2D/3D/All
    private final DatePicker datePicker = new DatePicker();

    private final Div hero = new Div();
    private final Div showtimeArea = new Div();

    private final Dialog seatDialog = new Dialog();
    private final Div seatMap = new Div();
    private final Div selectedSeatChips = new Div();
    private final Span totalPriceLabel = new Span("£0.00");

    private final TextField customerNameField = new TextField("Customer name");
    private final EmailField customerEmailField = new EmailField("Customer email");
    private final TextArea receiptArea = new TextArea("Booking receipt");
    private final Div receiptContainer = new Div();

    private final Div stepIndicatorArea = new Div();
    private final Div stepContentArea = new Div();

    private List<Screening> currentScreenings = List.of();
    private Long selectedFilmId;
    private Long requestedFilmId;
    private Screening selectedScreening;

    private List<BookingService.SeatOption> currentSeatOptions = List.of();
    private final Set<Long> selectedSeatIds = new LinkedHashSet<>();
    private final Map<Long, Seat> seatById = new LinkedHashMap<>();

    private BookingStep currentStep = BookingStep.SEATS;
    private boolean bookingCompleted = false;
    private String confirmedReceiptText = "";
    private String confirmedTotalText = "£0.00";
    private Booking confirmedBooking;
    private List<Seat> confirmedSeats = List.of();

    public BookingView(
            CinemaRepository cinemaRepository,
            ScreeningService screeningService,
            BookingService bookingService,
            PricingService pricingService,
            LoginService loginService
    ) {
        this.cinemaRepository = cinemaRepository;
        this.screeningService = screeningService;
        this.bookingService = bookingService;
        this.pricingService = pricingService;
        this.loginService = loginService;

        setWidthFull();
        getStyle()
                .set("background", DARK_BG)
                .set("min-height", "100vh")
                .set("color", "white");

        configureFilters();
        configureDialogFields();

        Div page = new Div();
        page.getStyle()
                .set("max-width", "1320px")
                .set("margin", "0 auto")
                .set("padding", "44px 48px 80px 48px")
                .set("box-sizing", "border-box");

        page.add(hero, buildSearchBar(), showtimeArea);
        add(page);

        reloadScreenings();
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long filmId) {
        this.requestedFilmId = filmId;
        reloadScreenings();
    }

    private void configureFilters() {
        cinemaBox.setPlaceholder("Select a cinema");
        cinemaBox.setItems(cinemaRepository.findAll());
        cinemaBox.setItemLabelGenerator(Cinema::toString);
        cinemaBox.setClearButtonVisible(true);

        filmBox.setPlaceholder("Select a film");
        filmBox.setItemLabelGenerator(Film::getTitle);
        filmBox.setClearButtonVisible(false);

        hallTypeBox.setPlaceholder("All hall types");
        hallTypeBox.setItems(HallType.values());
        hallTypeBox.setItemLabelGenerator(HallType::getLabel);
        hallTypeBox.setClearButtonVisible(true);

        formatBox.setPlaceholder("All formats");
        formatBox.setItems("All", "2D", "3D");
        formatBox.setValue("All");
        formatBox.setClearButtonVisible(false);

        datePicker.setPlaceholder("Select a date");
        datePicker.setValue(LocalDate.now());

        filmBox.addValueChangeListener(event -> {
            Film film = event.getValue();
            selectedFilmId = film == null ? null : film.getId();
            renderHero();
            renderShowtimes();
        });

        hallTypeBox.addValueChangeListener(event -> {
            renderShowtimes();
        });

        formatBox.addValueChangeListener(event -> {
            renderShowtimes();
        });
    }

    private void configureDialogFields() {
        seatDialog.setModal(true);
        seatDialog.setCloseOnEsc(true);
        seatDialog.setCloseOnOutsideClick(false);
        seatDialog.setWidth("100vw");
        seatDialog.setHeight("100vh");
        seatDialog.getElement().setAttribute("theme", "no-padding");

        customerNameField.setWidthFull();
        customerNameField.setPlaceholder("e.g. John Smith");

        customerEmailField.setWidthFull();
        customerEmailField.setPlaceholder("e.g. john@example.com");
        customerEmailField.setErrorMessage("Please enter a valid email address.");

        receiptArea.setWidthFull();
        receiptArea.setHeight("230px");
        receiptArea.setReadOnly(true);
        receiptArea.setVisible(false);

        receiptContainer.setWidthFull();
        receiptContainer.setVisible(false);
    }

    private Div buildSearchBar() {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("background", "white")
                .set("color", "#111827")
                .set("padding", "18px 22px")
                .set("box-shadow", "0 16px 40px rgba(0,0,0,0.28)")
                .set("margin", "32px 0 50px 0")
                .set("box-sizing", "border-box")
                .set("width", "100%")
                .set("max-width", "100%")
                .set("overflow", "hidden");

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(160px, 1fr))")
                .set("gap", "14px")
                .set("align-items", "end")
                .set("width", "100%")
                .set("box-sizing", "border-box");

        filmBox.setWidthFull();
        cinemaBox.setWidthFull();
        hallTypeBox.setWidthFull();
        formatBox.setWidthFull();
        datePicker.setWidthFull();

        /*
         * Vaadin input components have their own default minimum widths.
         * Setting min-width to 0 allows the CSS grid to shrink/wrap
         * instead of pushing the Search button outside the white panel.
         */
        filmBox.getStyle().set("min-width", "0");
        cinemaBox.getStyle().set("min-width", "0");
        hallTypeBox.getStyle().set("min-width", "0");
        formatBox.getStyle().set("min-width", "0");
        datePicker.getStyle().set("min-width", "0");

        Button searchButton = new Button("Search", event -> reloadScreenings());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.setWidthFull();
        searchButton.getStyle()
                .set("height", "52px")
                .set("min-width", "0")
                .set("font-size", "18px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.04em")
                .set("background", BLUE)
                .set("color", "white")
                .set("border-radius", "0")
                .set("box-sizing", "border-box")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)");

        grid.add(filmBox, cinemaBox, hallTypeBox, formatBox, datePicker, searchButton);
        wrapper.add(grid);

        return wrapper;
    }

    private void reloadScreenings() {
        LocalDate selectedDate = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
        Cinema selectedCinema = cinemaBox.getValue();

        /*
         * The booking search is date-specific. Previously this used selectedDate.plusDays(7),
         * which meant showtimes from several dates could appear together and look like
         * duplicate times on the page. We now load only the chosen date.
         */
        if (selectedCinema == null) {
            currentScreenings = screeningService.findScreeningsBetween(selectedDate, selectedDate);
        } else {
            currentScreenings = screeningService.findScreeningsByCinemaBetween(
                    selectedCinema.getId(),
                    selectedDate,
                    selectedDate
            );
        }

        currentScreenings = sortAndRemoveDuplicateShowtimes(currentScreenings);

        List<Film> films = currentScreenings.stream()
                .map(Screening::getFilm)
                .collect(Collectors.toMap(
                        Film::getId,
                        film -> film,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(Film::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();

        filmBox.setItems(films);

        if (films.isEmpty()) {
            selectedFilmId = null;
            filmBox.clear();
        } else {
            Film selectedFilm = null;

            if (requestedFilmId != null) {
                selectedFilm = films.stream()
                        .filter(film -> Objects.equals(film.getId(), requestedFilmId))
                        .findFirst()
                        .orElse(null);
            }

            if (selectedFilm == null && selectedFilmId != null) {
                selectedFilm = films.stream()
                        .filter(film -> Objects.equals(film.getId(), selectedFilmId))
                        .findFirst()
                        .orElse(null);
            }

            if (selectedFilm == null) {
                selectedFilm = films.get(0);
            }

            selectedFilmId = selectedFilm.getId();
            filmBox.setValue(selectedFilm);
        }

        renderHero();
        renderShowtimes();
    }

    private void renderHero() {
        hero.removeAll();

        Screening firstScreening = findFirstScreeningForSelectedFilm();

        if (firstScreening == null) {
            Div empty = new Div();
            empty.setText("No film selected.");
            empty.getStyle()
                    .set("padding", "80px 0")
                    .set("font-size", "24px")
                    .set("color", "#cbd5e1");
            hero.add(empty);
            return;
        }

        Film film = firstScreening.getFilm();

        Div heroGrid = new Div();
        heroGrid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "240px 1fr 260px")
                .set("gap", "64px")
                .set("align-items", "start");

        Div posterBlock = new Div();
        posterBlock.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "18px");

        Div poster = buildPoster(film);

        Button bookButton = new Button("Book Tickets", event -> scrollToShowtimes());
        bookButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        bookButton.getStyle()
                .set("height", "48px")
                .set("background", BLUE)
                .set("border-radius", "0")
                .set("font-weight", "800")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)");

        Button trailerButton = new Button("Play trailer");
        trailerButton.getStyle()
                .set("height", "48px")
                .set("background", "transparent")
                .set("color", "white")
                .set("border", "1px solid white")
                .set("border-radius", "0")
                .set("font-weight", "700");

        posterBlock.add(poster, bookButton, trailerButton);

        Div info = new Div();

        H1 title = new H1(film.getTitle().toUpperCase());
        title.getStyle()
                .set("font-size", "44px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.03em")
                .set("margin", "22px 0 34px 0");

        Span age = new Span(film.getAgeRating());
        age.getStyle()
                .set("font-size", "14px")
                .set("background", "#ff4fa3")
                .set("border-radius", "999px")
                .set("padding", "6px 9px")
                .set("margin-left", "12px")
                .set("vertical-align", "middle");

        title.add(age);

        if (hasAdvancePreviewForSelectedFilm()) {
            title.add(heroBadge("ADVANCE PREVIEW"));
        }

        info.add(
                title,
                infoBlock("DIRECTORS", film.getDirectors()),
                infoBlock("CAST", film.getActors()),
                infoBlock("SYNOPSIS", film.getDescription())
        );

        Div side = new Div();
        side.getStyle()
                .set("padding-top", "82px")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "26px");

        side.add(
                infoBlock("RUNTIME", formatRuntime(film.getDurationMinutes())),
                infoBlock("GENRE", film.getGenre()),
                infoBlock("RELEASE DATE", formatReleaseDate(film.getReleaseDate())),
                infoBlock("CONTENT ADVICE", film.getContentAdvice()),
                infoBlock("BOOKING TYPE", hasAdvancePreviewForSelectedFilm()
                        ? "Advance preview screenings are available before or around the official release window."
                        : "Regular public screenings")
        );

        heroGrid.add(posterBlock, info, side);
        hero.add(heroGrid);
    }

    private Span heroBadge(String text) {
        Span badge = new Span(text);
        badge.getStyle()
                .set("display", "inline-block")
                .set("margin-left", "14px")
                .set("padding", "6px 10px")
                .set("border", "1px solid #fb923c")
                .set("color", "#fdba74")
                .set("font-size", "13px")
                .set("font-weight", "900")
                .set("vertical-align", "middle")
                .set("letter-spacing", "0.08em");
        return badge;
    }

    private Span showtimeBadge(String text) {
        Span badge = new Span(text);
        badge.getStyle()
                .set("display", "inline-block")
                .set("margin-top", "8px")
                .set("padding", "4px 7px")
                .set("border", "1px solid #fb923c")
                .set("color", "#fdba74")
                .set("font-size", "11px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.05em");
        return badge;
    }

    private Span hallTypeBadge(HallType hallType) {
        Span badge = new Span(hallType.getLabel());
        String bg = "transparent";
        String border = "transparent";
        String color = "transparent";

        switch (hallType) {
            case IMAX -> {
                bg = "rgba(99, 102, 241, 0.22)";
                border = "#818cf8";
                color = "#c7d2fe";
            }
            case PREMIUM -> {
                bg = "rgba(234, 179, 8, 0.22)";
                border = "#eab308";
                color = "#fde68a";
            }
            default -> {
                bg = "transparent";
                border = "transparent";
                color = "transparent";
            }
        }

        badge.getStyle()
                .set("display", "inline-block")
                .set("margin-top", "8px")
                .set("margin-left", "6px")
                .set("padding", "4px 7px")
                .set("border", "1px solid " + border)
                .set("background", bg)
                .set("color", color)
                .set("font-size", "11px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.05em");
        return badge;
    }

    private Span formatBadge(String format) {
        Span badge = new Span(format);
        String bg = "rgba(16, 185, 129, 0.22)";
        String border = "#10b981";
        String color = "#6ee7b7";

        if (format.equals("3D")) {
            bg = "rgba(139, 92, 246, 0.22)";
            border = "#8b5cf6";
            color = "#c4b5fd";
        }

        badge.getStyle()
                .set("display", "inline-block")
                .set("margin-top", "8px")
                .set("margin-left", "6px")
                .set("padding", "4px 7px")
                .set("border", "1px solid " + border)
                .set("background", bg)
                .set("color", color)
                .set("font-size", "11px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.05em");
        return badge;
    }

    private boolean hasAdvancePreviewForSelectedFilm() {
        if (selectedFilmId == null) {
            return false;
        }

        return currentScreenings.stream()
                .filter(screening -> Objects.equals(screening.getFilm().getId(), selectedFilmId))
                .anyMatch(this::isAdvancePreview);
    }

    private boolean isAdvancePreview(Screening screening) {
        return screening != null && !screening.getScreeningType().isRegular();
    }

    private String screeningTypeLabel(Screening screening) {
        if (isAdvancePreview(screening)) {
            return "Advance Preview";
        }

        return "Regular";
    }

    private Div buildPoster(Film film) {
        Div poster = new Div();
        poster.getStyle()
                .set("width", "240px")
                .set("height", "360px")
                .set("background", "linear-gradient(145deg, #111827, #4c1d95)")
                .set("box-shadow", "0 18px 40px rgba(0,0,0,0.45)")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("text-align", "center")
                .set("font-weight", "900")
                .set("font-size", "20px")
                .set("overflow", "hidden");

        if (film.getPosterUrl() != null && !film.getPosterUrl().isBlank()) {
            Image image = new Image(film.getPosterUrl(), film.getTitle());
            image.setWidthFull();
            image.setHeightFull();
            image.getStyle().set("object-fit", "cover");
            poster.add(image);
        } else {
            poster.add(new Span(film.getTitle()));
        }

        return poster;
    }

    private Div infoBlock(String label, String value) {
        Div block = new Div();
        block.getStyle().set("margin-bottom", "22px");

        Span heading = new Span(label);
        heading.getStyle()
                .set("display", "block")
                .set("color", "#b9c4d4")
                .set("font-size", "15px")
                .set("letter-spacing", "0.04em")
                .set("margin-bottom", "8px");

        Paragraph text = new Paragraph(value == null || value.isBlank() ? "-" : value);
        text.getStyle()
                .set("margin", "0")
                .set("line-height", "1.55")
                .set("font-size", "16px")
                .set("color", "white");

        block.add(heading, text);
        return block;
    }

    private void renderShowtimes() {
        showtimeArea.removeAll();

        if (selectedFilmId == null) {
            return;
        }

        List<Screening> screenings = sortAndRemoveDuplicateShowtimes(
                currentScreenings.stream()
                        .filter(screening -> Objects.equals(screening.getFilm().getId(), selectedFilmId))
                        .filter(screening -> {
                            HallType selected = hallTypeBox.getValue();
                            return selected == null || screening.getScreen().getHallType() == selected;
                        })
                        .filter(screening -> {
                            String selectedFormat = formatBox.getValue();
                            if (selectedFormat == null || selectedFormat.equals("All")) {
                                return true;
                            }

                            return screening.getScreeningType() != null
                                    && selectedFormat.equals(screening.getScreeningType().getFormat());
                        })
                        .sorted(screeningComparator())
                        .toList()
        );

        if (screenings.isEmpty()) {
            Paragraph empty = new Paragraph("No showtimes are available for this film, date, hall type and format combination.");
            empty.getStyle().set("color", "#cbd5e1");
            showtimeArea.add(empty);
            return;
        }

        Map<String, List<Screening>> byCinemaAndHallType = screenings.stream()
                .collect(Collectors.groupingBy(
                        screening -> screening.getScreen().getCinema().getName()
                                + " | " + screening.getScreen().getHallType().getLabel()
                                + " Hall",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<String, List<Screening>> entry : byCinemaAndHallType.entrySet()) {
            showtimeArea.add(buildCinemaShowtimeSection(entry.getKey(), entry.getValue()));
        }
    }

    private Div buildCinemaShowtimeSection(String cinemaName, List<Screening> screenings) {
        screenings = sortAndRemoveDuplicateShowtimes(screenings);

        Div section = new Div();
        section.getStyle().set("margin-bottom", "54px");

        H2 cinemaTitle = new H2(cinemaName.toUpperCase());
        cinemaTitle.getStyle()
                .set("font-size", "38px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.03em")
                .set("margin", "0 0 22px 0");

        Span allShowtimes = new Span("All Showtimes");
        allShowtimes.getStyle()
                .set("display", "inline-block")
                .set("color", "#cbd5e1")
                .set("margin-bottom", "10px")
                .set("font-size", "15px");

        Div priceLine = new Div();
        priceLine.setText("Adult price from " + estimateLowestPrice(screenings));
        priceLine.getStyle()
                .set("font-size", "18px")
                .set("font-weight", "700")
                .set("margin-bottom", "22px");

        Div cardGrid = new Div();
        cardGrid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(160px, 1fr))")
                .set("gap", "22px");

        for (Screening screening : screenings) {
            cardGrid.add(buildShowtimeCard(screening));
        }

        section.add(cinemaTitle, allShowtimes, priceLine, cardGrid);
        return section;
    }

    private Div buildShowtimeCard(Screening screening) {
        Div card = new Div();
        card.getStyle()
                .set("border", "1px solid rgba(255,255,255,0.35)")
                .set("min-height", "116px")
                .set("display", "grid")
                .set("grid-template-rows", "1fr 38px")
                .set("background", DARK_BG)
                .set("box-shadow", "0 10px 28px rgba(0,0,0,0.2)");

        Div timeBlock = new Div();
        timeBlock.getStyle()
                .set("padding", "12px")
                .set("box-sizing", "border-box");

        Span time = new Span(screening.getStartTime().toString());
        time.getStyle()
                .set("display", "block")
                .set("font-size", "28px")
                .set("font-weight", "850")
                .set("line-height", "1");

        Span screen = new Span("Screen " + screening.getScreen().getScreenNumber());
        screen.getStyle()
                .set("display", "block")
                .set("color", "#cbd5e1")
                .set("margin-top", "8px")
                .set("font-size", "15px");

        timeBlock.add(time, screen);

        if (isAdvancePreview(screening)) {
            timeBlock.add(showtimeBadge("ADVANCE PREVIEW"));
        }

        // Add 2D/3D format badge
        String format = screening.getFormat();
        if (format != null) {
            timeBlock.add(formatBadge(format));
        }

        HallType hallType = screening.getScreen().getHallType();
        if (hallType != HallType.REGULAR) {
            timeBlock.add(hallTypeBadge(hallType));
        }

        Button bookNow = new Button(isAdvancePreview(screening) ? "Book Early" : "Book Now", event -> openSeatDialog(screening));
        bookNow.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        bookNow.getStyle()
                .set("border-radius", "0")
                .set("background", BLUE)
                .set("font-weight", "800")
                .set("height", "38px")
                .set("width", "100%")
                .set("clip-path", "polygon(0 0, 100% 0, 94% 100%, 0 100%)");

        card.add(timeBlock, bookNow);
        return card;
    }

    private void openSeatDialog(Screening screening) {
        selectedScreening = screening;
        selectedSeatIds.clear();
        seatById.clear();
        currentStep = BookingStep.SEATS;
        bookingCompleted = false;
        confirmedReceiptText = "";
        confirmedTotalText = "£0.00";
        confirmedBooking = null;
        confirmedSeats = List.of();
        receiptArea.clear();
        receiptArea.setVisible(false);
        receiptContainer.removeAll();
        receiptContainer.setVisible(false);
        customerNameField.clear();
        customerEmailField.clear();

        loadSeatMap(screening);

        seatDialog.removeAll();

        Div content = new Div();
        content.getStyle()
                .set("width", "100vw")
                .set("height", "100vh")
                .set("background", LIGHT_BG)
                .set("color", LIGHT_TEXT)
                .set("padding", "34px 48px")
                .set("box-sizing", "border-box")
                .set("overflow", "auto");

        content.add(buildDialogHeader(screening), stepIndicatorArea, stepContentArea);
        seatDialog.add(content);

        renderCurrentBookingStep();
        seatDialog.open();
    }

    private Div buildDialogHeader(Screening screening) {
        Div top = new Div();
        top.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1.1fr 1fr")
                .set("gap", "28px")
                .set("align-items", "start")
                .set("margin-bottom", "28px");

        Div left = new Div();

        H1 title = new H1(screening.getFilm().getTitle().toUpperCase());
        title.getStyle()
                .set("font-size", "34px")
                .set("line-height", "1.15")
                .set("font-weight", "900")
                .set("letter-spacing", "0.03em")
                .set("color", LIGHT_TEXT)
                .set("margin", "0 0 18px 0");

        Button back = new Button("‹ Back", event -> seatDialog.close());
        back.getStyle()
                .set("background", "transparent")
                .set("color", BLUE)
                .set("font-size", "18px")
                .set("font-weight", "800")
                .set("padding", "0");

        left.add(title, back);

        Div right = new Div();
        right.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr 1fr 1fr")
                .set("gap", "20px")
                .set("background", LIGHT_PANEL)
                .set("border", "1px solid " + LIGHT_BORDER)
                .set("border-radius", "18px")
                .set("padding", "20px")
                .set("box-shadow", "0 12px 32px rgba(15, 23, 42, 0.08)");

        right.add(
                dialogInfo("CINEMA", screening.getScreen().getCinema().getName()),
                dialogInfo("SHOWTIME", screening.getScreeningDate() + ", " + screening.getStartTime()),
                dialogInfo("HALL TYPE", screening.getScreen().getHallType().getLabel() + " Hall"),
                dialogInfo("SCREENING TYPE", screeningTypeLabel(screening))
        );

        top.add(left, right);
        return top;
    }

    private Div dialogInfo(String label, String value) {
        Div box = new Div();

        Span h = new Span(label);
        h.getStyle()
                .set("display", "block")
                .set("color", LIGHT_MUTED)
                .set("font-size", "13px")
                .set("letter-spacing", "0.08em")
                .set("font-weight", "900")
                .set("margin-bottom", "8px");

        Span v = new Span(value == null || value.isBlank() ? "-" : value);
        v.getStyle()
                .set("display", "block")
                .set("font-size", "16px")
                .set("font-weight", "850")
                .set("color", LIGHT_TEXT)
                .set("line-height", "1.35");

        box.add(h, v);
        return box;
    }

    private void renderCurrentBookingStep() {
        stepIndicatorArea.removeAll();
        stepContentArea.removeAll();

        stepIndicatorArea.add(buildSteps());

        switch (currentStep) {
            case SEATS -> stepContentArea.add(buildSeatsStep());
            case TICKETS -> stepContentArea.add(buildTicketsStep());
            case SUMMARY -> stepContentArea.add(buildSummaryStep());
            case PAYMENT -> stepContentArea.add(buildPaymentStep());
        }
    }

    private Div buildSteps() {
        Div steps = new Div();
        steps.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(4, 1fr)")
                .set("max-width", "820px")
                .set("margin", "0 auto 28px auto")
                .set("gap", "12px");

        steps.add(
                stepItem("1", "Seats", BookingStep.SEATS),
                stepItem("2", "Tickets", BookingStep.TICKETS),
                stepItem("3", "Summary", BookingStep.SUMMARY),
                stepItem("4", "Payment", BookingStep.PAYMENT)
        );

        return steps;
    }

    private Div stepItem(String number, String label, BookingStep step) {
        boolean active = currentStep == step;
        boolean completed = step.ordinal() < currentStep.ordinal();

        Div item = new Div();
        item.getStyle()
                .set("text-align", "center")
                .set("padding", "12px 10px")
                .set("border-radius", "16px")
                .set("background", active ? BLUE : completed ? "#dbeafe" : LIGHT_PANEL)
                .set("color", active ? "white" : completed ? "#005ba6" : LIGHT_MUTED)
                .set("border", "1px solid " + (active ? BLUE : LIGHT_BORDER))
                .set("box-shadow", active ? "0 12px 30px rgba(0,114,206,0.22)" : "none");

        Span circle = new Span(number);
        circle.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("width", "28px")
                .set("height", "28px")
                .set("border-radius", "999px")
                .set("border", active ? "2px solid white" : "2px solid currentColor")
                .set("font-weight", "900")
                .set("margin-bottom", "7px");

        Span text = new Span(label);
        text.getStyle()
                .set("display", "block")
                .set("font-size", "15px")
                .set("font-weight", "900");

        item.add(circle, text);

        if (completed && !bookingCompleted) {
            item.addClickListener(event -> {
                currentStep = step;
                renderCurrentBookingStep();
            });
            item.getStyle().set("cursor", "pointer");
        }

        return item;
    }

    private Div buildSeatsStep() {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("max-width", "960px")
                .set("margin", "0 auto");

        H2 heading = sectionHeading("Choose your seats");
        Div priceBox = buildDialogPriceBox();

        Button next = primaryButton("Continue to tickets", event -> {
            if (selectedSeatIds.isEmpty()) {
                Notification.show("Please select at least one seat first.");
                return;
            }

            currentStep = BookingStep.TICKETS;
            renderCurrentBookingStep();
        });

        wrapper.add(heading, seatMap, priceBox, centerRow(next));
        return wrapper;
    }

    private Div buildTicketsStep() {
        Div wrapper = cardPanel();

        H2 heading = sectionHeading("Ticket details");

        Paragraph help = new Paragraph("Enter the customer details for this booking.");
        help.getStyle()
                .set("color", LIGHT_MUTED)
                .set("font-size", "16px")
                .set("margin", "0 0 22px 0");

        Div selectedSeats = new Div();
        selectedSeats.setText("Selected seats: " + selectedSeatNumbersText());
        selectedSeats.getStyle()
                .set("margin", "0 0 22px 0")
                .set("padding", "16px")
                .set("background", LIGHT_PANEL_SOFT)
                .set("border-radius", "14px")
                .set("font-weight", "800")
                .set("color", LIGHT_TEXT);

        Div form = new Div();
        form.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "16px");

        customerNameField.setWidthFull();
        customerEmailField.setWidthFull();
        form.add(customerNameField, customerEmailField);

        Button back = secondaryButton("Back to seats", event -> {
            currentStep = BookingStep.SEATS;
            renderCurrentBookingStep();
        });

        Button next = primaryButton("Continue to summary", event -> {
            if (customerNameField.isEmpty()) {
                Notification.show("Please enter customer name.");
                return;
            }

            if (customerEmailField.isEmpty() || customerEmailField.isInvalid() || !customerEmailField.getValue().contains("@")) {
                Notification.show("Please enter a valid customer email.");
                return;
            }

            currentStep = BookingStep.SUMMARY;
            renderCurrentBookingStep();
        });

        wrapper.add(heading, help, selectedSeats, form, actionRow(back, next));
        return wrapper;
    }

    private Div buildSummaryStep() {
        Div wrapper = cardPanel();

        H2 heading = sectionHeading("Booking summary");

        TextArea summary = new TextArea();
        summary.setWidthFull();
        summary.setHeight("280px");
        summary.setReadOnly(true);
        summary.setValue(buildBookingPreview());
        summary.getStyle()
                .set("font-family", "monospace")
                .set("font-size", "14px");

        Button back = secondaryButton("Back to tickets", event -> {
            currentStep = BookingStep.TICKETS;
            renderCurrentBookingStep();
        });

        Button next = primaryButton("Continue to payment", event -> {
            currentStep = BookingStep.PAYMENT;
            renderCurrentBookingStep();
        });

        wrapper.add(heading, summary, actionRow(back, next));
        return wrapper;
    }

    private Div buildPaymentStep() {
        Div wrapper = cardPanel();

        H2 heading = sectionHeading(bookingCompleted ? "Booking confirmed" : "Payment");

        Paragraph note = new Paragraph(
                bookingCompleted
                        ? "The booking has been created successfully. The receipt below can be copied into the report evidence."
                        : "Payment is simulated in this coursework system. Click Confirm booking to create the booking and generate the receipt."
        );
        note.getStyle()
                .set("color", LIGHT_MUTED)
                .set("font-size", "16px")
                .set("line-height", "1.6")
                .set("margin", "0 0 24px 0");

        Div total = new Div();
        total.getStyle()
                .set("padding", "20px")
                .set("background", LIGHT_PANEL_SOFT)
                .set("border-radius", "14px")
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "center")
                .set("font-weight", "900")
                .set("margin-bottom", "22px");

        Span label = new Span(bookingCompleted ? "Paid total" : "Total to pay");
        Span amount = new Span(bookingCompleted ? confirmedTotalText : currentTotalPriceText());
        amount.getStyle()
                .set("font-size", "28px")
                .set("color", BLUE);

        total.add(label, amount);

        if (bookingCompleted) {
            receiptContainer.removeAll();

            if (confirmedBooking != null) {
                receiptContainer.add(buildTicketReceiptCard(
                        confirmedBooking,
                        selectedScreening,
                        confirmedSeats
                ));
            } else {
                receiptArea.setValue(confirmedReceiptText);
                receiptArea.setVisible(true);
                receiptContainer.add(receiptArea);
            }

            receiptContainer.setVisible(true);

            Button close = primaryButton("Close", event -> seatDialog.close());
            wrapper.add(heading, note, total, receiptContainer, centerRow(close));
            return wrapper;
        }

        Button back = secondaryButton("Back to summary", event -> {
            currentStep = BookingStep.SUMMARY;
            renderCurrentBookingStep();
        });

        Button confirm = primaryButton("Confirm booking", event -> createBooking());

        wrapper.add(heading, note, total, actionRow(back, confirm));
        return wrapper;
    }

    private H2 sectionHeading(String text) {
        H2 heading = new H2(text.toUpperCase());
        heading.getStyle()
                .set("text-align", "center")
                .set("font-size", "28px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.08em")
                .set("color", LIGHT_TEXT)
                .set("margin", "0 0 22px 0");
        return heading;
    }

    private Div cardPanel() {
        Div panel = new Div();
        panel.getStyle()
                .set("max-width", "820px")
                .set("margin", "0 auto")
                .set("background", LIGHT_PANEL)
                .set("border", "1px solid " + LIGHT_BORDER)
                .set("border-radius", "22px")
                .set("padding", "32px")
                .set("box-shadow", "0 18px 45px rgba(15, 23, 42, 0.10)");
        return panel;
    }

    private Button primaryButton(String text, ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(text, listener);
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.getStyle()
                .set("height", "48px")
                .set("background", BLUE)
                .set("color", "white")
                .set("font-weight", "900")
                .set("border-radius", "0")
                .set("padding", "0 34px")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)");
        return button;
    }

    private Button secondaryButton(String text, ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(text, listener);
        button.getStyle()
                .set("height", "48px")
                .set("background", "white")
                .set("color", LIGHT_TEXT)
                .set("font-weight", "900")
                .set("border", "1px solid " + LIGHT_BORDER)
                .set("border-radius", "0")
                .set("padding", "0 28px");
        return button;
    }

    private Div centerRow(Button button) {
        Div row = new Div(button);
        row.getStyle()
                .set("display", "flex")
                .set("justify-content", "center")
                .set("margin-top", "24px");
        return row;
    }

    private Div actionRow(Button left, Button right) {
        Div row = new Div(left, right);
        row.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("gap", "16px")
                .set("margin-top", "28px");
        return row;
    }

    private Div buildDialogPriceBox() {
        Div priceBox = new Div();
        priceBox.getStyle()
                .set("max-width", "780px")
                .set("margin", "22px auto")
                .set("padding", "18px")
                .set("border", "1px solid " + LIGHT_BORDER)
                .set("border-radius", "18px")
                .set("background", LIGHT_PANEL)
                .set("box-shadow", "0 10px 28px rgba(15, 23, 42, 0.06)");

        selectedSeatChips.getStyle()
                .set("display", "flex")
                .set("gap", "8px")
                .set("flex-wrap", "wrap")
                .set("justify-content", "center")
                .set("min-height", "32px");

        Div totalRow = new Div();
        totalRow.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("margin-top", "16px")
                .set("padding-top", "16px")
                .set("border-top", "1px solid " + LIGHT_BORDER);

        Span totalText = new Span("Total price");
        totalText.getStyle()
                .set("font-weight", "900")
                .set("color", LIGHT_TEXT);

        totalPriceLabel.getStyle()
                .set("font-weight", "900")
                .set("font-size", "24px")
                .set("color", BLUE);

        totalRow.add(totalText, totalPriceLabel);
        priceBox.add(selectedSeatChips, totalRow);

        updatePriceSummary();
        return priceBox;
    }

    private void loadSeatMap(Screening screening) {
        currentSeatOptions = bookingService.findSeatOptions(screening.getId())
                .stream()
                .sorted(seatOptionComparator())
                .toList();

        seatById.clear();

        for (BookingService.SeatOption option : currentSeatOptions) {
            seatById.put(option.seat().getId(), option.seat());
        }

        renderSeatMap();
    }

    private void renderSeatMap() {
        seatMap.removeAll();
        seatMap.getStyle()
                .set("max-width", "780px")
                .set("margin", "0 auto")
                .set("padding", "30px")
                .set("border-radius", "22px")
                .set("background", LIGHT_PANEL)
                .set("border", "1px solid " + LIGHT_BORDER)
                .set("box-shadow", "0 18px 45px rgba(15, 23, 42, 0.10)")
                .set("overflow-x", "auto");

        HallType hallType = selectedScreening.getScreen().getHallType();
        int seatsPerRow = getSeatsPerRowForHall(hallType);

        Div screen = new Div();
        screen.setText("SCREEN - " + hallType.getLabel() + " Hall");
        screen.getStyle()
                .set("height", "34px")
                .set("line-height", "34px")
                .set("text-align", "center")
                .set("border-radius", "50% 50% 8px 8px")
                .set("background", hallType == HallType.IMAX ? "linear-gradient(180deg, #dbeafe, #818cf8)" : hallType == HallType.PREMIUM ? "linear-gradient(180deg, #fef3c7, #eab308)" : "linear-gradient(180deg, #dbeafe, #93c5fd)")
                .set("color", hallType == HallType.PREMIUM ? "#1e3a8a" : "#1e3a8a")
                .set("font-weight", "900")
                .set("letter-spacing", "4px")
                .set("margin", "0 auto 24px auto")
                .set("max-width", "520px");

        Div hallInfo = new Div();
        hallInfo.getStyle()
                .set("text-align", "center")
                .set("margin-bottom", "18px")
                .set("font-weight", "800")
                .set("font-size", "16px")
                .set("color", LIGHT_TEXT);

        Span hallLabel = new Span(hallType.getLabel() + " Hall · " + seatsPerRow + " seats per row · ");
        hallLabel.getStyle().set("color", LIGHT_MUTED);

        Span hallPriceInfo = new Span("Base price from " + formatMoney(pricingService.getHallSurcharge(hallType).add(new BigDecimal("5.00"))));
        hallPriceInfo.getStyle().set("color", BLUE).set("font-weight", "900");

        hallInfo.add(hallLabel, hallPriceInfo);

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(" + seatsPerRow + ", 44px)")
                .set("gap", "10px")
                .set("justify-content", "center")
                .set("min-width", seatsPerRow * 44 + "px");

        for (BookingService.SeatOption option : currentSeatOptions) {
            grid.add(createSeatButton(option, hallType));
        }

        seatMap.add(screen, hallInfo, buildSeatLegend(hallType), grid);
        updatePriceSummary();
    }

    private Button createSeatButton(BookingService.SeatOption option, HallType hallType) {
        Seat seat = option.seat();
        boolean selected = selectedSeatIds.contains(seat.getId());
        SeatType seatType = seat.getSeatType();

        Button button = new Button(seat.getSeatNumber());
        int buttonWidth = hallType == HallType.PREMIUM ? 54 : 44;
        button.setWidth(buttonWidth + "px");
        button.setHeight("38px");

        button.getStyle()
                .set("border-radius", "10px")
                .set("font-size", "13px")
                .set("font-weight", "800")
                .set("padding", "0")
                .set("cursor", option.available() ? "pointer" : "not-allowed")
                .set("box-shadow", "0 3px 8px rgba(0,0,0,0.18)");

        if (!option.available()) {
            button.setEnabled(false);
            button.getStyle()
                    .set("background", "#94a3b8")
                    .set("color", "#e2e8f0")
                    .set("border", "1px solid #94a3b8");
            return button;
        }

        if (selected) {
            button.getStyle()
                    .set("background", BLUE)
                    .set("color", "white")
                    .set("border", "1px solid #38bdf8");
        } else {
            String seatBg;
            String seatBorder;
            String seatTextColor;

            switch (seatType) {
                case CENTER -> {
                    seatBg = "#fef3c7";
                    seatBorder = "#eab308";
                    seatTextColor = "#111827";
                }
                case PREMIUM -> {
                    seatBg = "#dbeafe";
                    seatBorder = "#93c5fd";
                    seatTextColor = "#111827";
                }
                default -> {
                    seatBg = "white";
                    seatBorder = "#d1d5db";
                    seatTextColor = "#111827";
                }
            }

            button.getStyle()
                    .set("background", seatBg)
                    .set("color", seatTextColor)
                    .set("border", "1px solid " + seatBorder);
        }

        button.addClickListener(event -> {
            if (selectedSeatIds.contains(seat.getId())) {
                selectedSeatIds.remove(seat.getId());
            } else {
                selectedSeatIds.add(seat.getId());
            }

            renderSeatMap();
        });

        return button;
    }

    private int getSeatsPerRowForHall(HallType hallType) {
        return switch (hallType) {
            case IMAX -> 8;
            case PREMIUM -> 6;
            default -> 10;
        };
    }

    private HorizontalLayout buildSeatLegend(HallType hallType) {
        HorizontalLayout legend = new HorizontalLayout();

        legend.add(
                legendItem("white", "Standard (Front/Back rows)"),
                legendItem("#dbeafe", "Premium (Middle rows)"),
                legendItem("#fef3c7", "Center (Middle rows, center seats)"),
                legendItem(BLUE, "Selected"),
                legendItem("#94a3b8", "Booked")
        );

        legend.setWidthFull();
        legend.setJustifyContentMode(JustifyContentMode.CENTER);
        legend.getStyle()
                .set("gap", "18px")
                .set("margin-bottom", "20px")
                .set("flex-wrap", "wrap");

        return legend;
    }

    private Div legendItem(String color, String text) {
        Div item = new Div();
        item.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "8px")
                .set("font-size", "13px")
                .set("font-weight", "700")
                .set("color", LIGHT_TEXT);

        Span dot = new Span();
        dot.getStyle()
                .set("width", "16px")
                .set("height", "16px")
                .set("border-radius", "6px")
                .set("background", color)
                .set("display", "inline-block")
                .set("border", "1px solid #d1d5db");

        item.add(dot, new Span(text));
        return item;
    }

    private void createBooking() {
        if (selectedScreening == null) {
            Notification.show("Please select a screening.");
            return;
        }

        if (selectedSeatIds.isEmpty()) {
            Notification.show("Please select at least one seat.");
            currentStep = BookingStep.SEATS;
            renderCurrentBookingStep();
            return;
        }

        if (customerNameField.isEmpty()) {
            Notification.show("Please enter customer name.");
            currentStep = BookingStep.TICKETS;
            renderCurrentBookingStep();
            return;
        }

        if (customerEmailField.isEmpty() || !customerEmailField.getValue().contains("@")) {
            Notification.show("Please enter a valid customer email.");
            currentStep = BookingStep.TICKETS;
            renderCurrentBookingStep();
            return;
        }

        List<Seat> selectedSeats = selectedSeatIds.stream()
                .map(seatById::get)
                .filter(Objects::nonNull)
                .sorted(seatComparator())
                .toList();

        try {
            Booking booking = bookingService.createBooking(
                    selectedScreening.getId(),
                    selectedSeatIds,
                    customerNameField.getValue(),
                    customerEmailField.getValue()
            );

            bookingCompleted = true;
            confirmedBooking = booking;
            confirmedSeats = selectedSeats;
            confirmedTotalText = formatMoney(booking.getTotalCost());
            confirmedReceiptText = buildReceipt(booking, selectedScreening, selectedSeats);

            receiptContainer.removeAll();
            receiptContainer.add(buildTicketReceiptCard(booking, selectedScreening, selectedSeats));
            receiptContainer.setVisible(true);

            Notification.show("Booking created successfully.");

            selectedSeatIds.clear();
            loadSeatMap(selectedScreening);
            reloadScreenings();

            currentStep = BookingStep.PAYMENT;
            renderCurrentBookingStep();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void updatePriceSummary() {
        selectedSeatChips.removeAll();

        if (selectedScreening == null || selectedSeatIds.isEmpty()) {
            Span empty = new Span("No seats selected");
            empty.getStyle()
                    .set("color", LIGHT_MUTED)
                    .set("font-weight", "700");
            selectedSeatChips.add(empty);
            totalPriceLabel.setText("£0.00");
            return;
        }

        List<Seat> selectedSeats = selectedSeatIds.stream()
                .map(seatById::get)
                .filter(Objects::nonNull)
                .sorted(seatComparator())
                .toList();

        for (Seat seat : selectedSeats) {
            BigDecimal seatPrice = pricingService.calculateTicketPrice(selectedScreening, seat);
            String seatTypeLabel = getSeatTypeLabel(seat.getSeatType());
            Span chip = new Span(seat.getSeatNumber() + " (" + seatTypeLabel + " - " + formatMoney(seatPrice) + ")");
            chip.getStyle()
                    .set("background", "#dbeafe")
                    .set("color", "#1e40af")
                    .set("padding", "6px 10px")
                    .set("border-radius", "999px")
                    .set("font-weight", "800")
                    .set("font-size", "13px");

            selectedSeatChips.add(chip);
        }

        BigDecimal total = pricingService.calculateTotalPrice(selectedScreening, selectedSeats);
        totalPriceLabel.setText(formatMoney(total));
    }

    private String selectedSeatNumbersText() {
        return selectedSeatIds.stream()
                .map(seatById::get)
                .filter(Objects::nonNull)
                .sorted(seatComparator())
                .map(Seat::getSeatNumber)
                .collect(Collectors.joining(", "));
    }

    private String currentTotalPriceText() {
        if (selectedScreening == null || selectedSeatIds.isEmpty()) {
            return "£0.00";
        }

        List<Seat> selectedSeats = selectedSeatIds.stream()
                .map(seatById::get)
                .filter(Objects::nonNull)
                .sorted(seatComparator())
                .toList();

        return formatMoney(pricingService.calculateTotalPrice(selectedScreening, selectedSeats));
    }

    private Component buildTicketReceiptCard(Booking booking, Screening screening, List<Seat> selectedSeats) {
        Div outer = new Div();
        outer.setWidthFull();
        outer.getStyle()
                .set("display", "flex")
                .set("justify-content", "center")
                .set("margin-top", "24px");

        Div ticket = new Div();
        ticket.getStyle()
                .set("width", "380px")
                .set("max-width", "100%")
                .set("background", "linear-gradient(180deg, #ef6b63 0%, #d94f47 100%)")
                .set("border-radius", "30px")
                .set("padding", "22px")
                .set("box-shadow", "0 22px 55px rgba(15, 23, 42, 0.28)")
                .set("color", "white")
                .set("position", "relative")
                .set("overflow", "hidden")
                .set("box-sizing", "border-box");

        Div topBar = new Div();
        topBar.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "center")
                .set("margin-bottom", "18px");

        Span ticketFor = new Span("Ticket for " + selectedSeats.size() + " person" + (selectedSeats.size() > 1 ? "s" : ""));
        ticketFor.getStyle()
                .set("border", "1px dashed rgba(255,255,255,0.55)")
                .set("border-radius", "12px")
                .set("padding", "9px 14px")
                .set("font-size", "14px")
                .set("font-weight", "850")
                .set("background", "rgba(255,255,255,0.10)");

        Span status = new Span(booking.getStatus().name());
        status.getStyle()
                .set("font-size", "12px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.08em")
                .set("background", "rgba(255,255,255,0.18)")
                .set("border-radius", "999px")
                .set("padding", "8px 10px");

        topBar.add(ticketFor, status);

        H2 title = new H2(screening.getFilm().getTitle());
        title.getStyle()
                .set("font-size", "30px")
                .set("line-height", "1.1")
                .set("font-weight", "950")
                .set("margin", "0 0 4px 0")
                .set("text-align", "center")
                .set("color", "white");

        Span subtitle = new Span("HCBS Movie Ticket");
        subtitle.getStyle()
                .set("display", "block")
                .set("text-align", "center")
                .set("font-size", "14px")
                .set("font-weight", "750")
                .set("opacity", "0.88")
                .set("margin-bottom", "18px");

        Component poster = buildTicketPoster(screening);

        Div timePrice = new Div();
        timePrice.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr 1fr")
                .set("gap", "8px")
                .set("margin", "16px 0 14px 0");

        timePrice.add(
                ticketMeta("Date", screening.getScreeningDate().format(DateTimeFormatter.ofPattern("dd MMM"))),
                ticketMeta("Time", screening.getStartTime().toString()),
                ticketMeta("Price", formatMoney(booking.getTotalCost()))
        );

        Div seatStats = new Div();
        seatStats.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(4, 1fr)")
                .set("gap", "12px")
                .set("padding", "16px 0")
                .set("margin", "10px 0 16px 0")
                .set("border-top", "1px dashed rgba(255,255,255,0.45)")
                .set("border-bottom", "1px dashed rgba(255,255,255,0.45)");

        seatStats.add(
                ticketStat("HALL", selectedScreening.getScreen().getHallType().getLabel()),
                ticketStat("SCREEN", String.valueOf(screening.getScreen().getScreenNumber())),
                ticketStat("ROW", extractRowText(selectedSeats)),
                ticketStat("SEAT", extractSeatText(selectedSeats))
        );

        Div details = new Div();
        details.getStyle()
                .set("display", "grid")
                .set("gap", "6px")
                .set("font-size", "13px")
                .set("line-height", "1.45")
                .set("margin-bottom", "16px");

        details.add(
                ticketDetail("Booking Ref", booking.getBookingReference()),
                ticketDetail("Cinema", screening.getScreen().getCinema().getName()),
                ticketDetail("City", screening.getScreen().getCinema().getCity()),
                ticketDetail("Hall Type", screening.getScreen().getHallType().getLabel()),
                ticketDetail("Screening", screeningTypeLabel(screening)),
                ticketDetail("Customer", safeText(booking.getCustomerName())),
                ticketDetail("Booked At", booking.getBookingDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        );

        Div barcode = buildFakeBarcode();

        ticket.add(topBar, title, subtitle, poster, timePrice, seatStats, details, barcode);
        outer.add(ticket);
        return outer;
    }

    private Component buildTicketPoster(Screening screening) {
        Div holder = new Div();
        holder.getStyle()
                .set("height", "220px")
                .set("border-radius", "20px")
                .set("overflow", "hidden")
                .set("background", "rgba(255,255,255,0.16)")
                .set("box-shadow", "inset 0 0 0 1px rgba(255,255,255,0.18)");

        String posterUrl = screening.getFilm().getPosterUrl();

        if (posterUrl != null && !posterUrl.isBlank()) {
            Image image = new Image(posterUrl, screening.getFilm().getTitle());
            image.setWidthFull();
            image.setHeightFull();
            image.getStyle()
                    .set("object-fit", "cover")
                    .set("display", "block");
            holder.add(image);
        } else {
            Span fallback = new Span(screening.getFilm().getTitle());
            fallback.getStyle()
                    .set("height", "100%")
                    .set("display", "flex")
                    .set("align-items", "center")
                    .set("justify-content", "center")
                    .set("padding", "18px")
                    .set("text-align", "center")
                    .set("font-size", "22px")
                    .set("font-weight", "900");
            holder.add(fallback);
        }

        return holder;
    }

    private Div ticketMeta(String label, String value) {
        Div box = new Div();
        box.getStyle()
                .set("background", "rgba(255,255,255,0.13)")
                .set("border", "1px solid rgba(255,255,255,0.16)")
                .set("border-radius", "14px")
                .set("padding", "10px")
                .set("box-sizing", "border-box");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("display", "block")
                .set("font-size", "11px")
                .set("font-weight", "750")
                .set("opacity", "0.78")
                .set("margin-bottom", "4px");

        Span valueSpan = new Span(value == null || value.isBlank() ? "-" : value);
        valueSpan.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("font-weight", "900");

        box.add(labelSpan, valueSpan);
        return box;
    }

    private Div ticketStat(String label, String value) {
        Div box = new Div();
        box.getStyle()
                .set("text-align", "center");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("display", "block")
                .set("font-size", "11px")
                .set("font-weight", "850")
                .set("letter-spacing", "0.08em")
                .set("opacity", "0.75")
                .set("margin-bottom", "4px");

        Span valueSpan = new Span(value == null || value.isBlank() ? "-" : value);
        valueSpan.getStyle()
                .set("display", "block")
                .set("font-size", "25px")
                .set("font-weight", "950");

        box.add(labelSpan, valueSpan);
        return box;
    }

    private Div ticketDetail(String label, String value) {
        Div row = new Div();
        row.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "112px 1fr")
                .set("gap", "12px")
                .set("align-items", "baseline");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("font-weight", "800")
                .set("opacity", "0.74");

        Span valueSpan = new Span(value == null || value.isBlank() ? "-" : value);
        valueSpan.getStyle()
                .set("font-weight", "900")
                .set("text-align", "right")
                .set("overflow-wrap", "anywhere");

        row.add(labelSpan, valueSpan);
        return row;
    }

    private Div buildFakeBarcode() {
        Div barcode = new Div();
        barcode.getStyle()
                .set("height", "58px")
                .set("border-radius", "12px")
                .set("background",
                        "repeating-linear-gradient(to right, " +
                                "#ffffff 0px, #ffffff 2px, " +
                                "transparent 2px, transparent 5px, " +
                                "#ffffff 5px, #ffffff 8px, " +
                                "transparent 8px, transparent 12px)")
                .set("background-color", "rgba(255,255,255,0.12)")
                .set("opacity", "0.92");

        return barcode;
    }

    private String extractRowText(List<Seat> seats) {
        if (seats == null || seats.isEmpty()) {
            return "-";
        }

        return seats.stream()
                .map(Seat::getSeatNumber)
                .filter(seatNumber -> seatNumber != null && !seatNumber.isBlank())
                .map(seatNumber -> seatNumber.substring(0, 1))
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String extractSeatText(List<Seat> seats) {
        if (seats == null || seats.isEmpty()) {
            return "-";
        }

        return seats.stream()
                .map(Seat::getSeatNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String getSeatTypeLabel(SeatType seatType) {
        return switch (seatType) {
            case STANDARD -> "Standard";
            case PREMIUM -> "Premium";
            case CENTER -> "Center";
        };
    }

    private String buildBookingPreview() {
        List<Seat> selectedSeats = selectedSeatIds.stream()
                .map(seatById::get)
                .filter(Objects::nonNull)
                .sorted(seatComparator())
                .toList();

        return """
                Film: %s
                Cinema: %s
                City: %s
                Date: %s
                Showing Time: %s
                Hall Type: %s
                Screen: %s
                Screening Type: %s
                Customer Name: %s
                Customer Email: %s
                Number of Tickets: %d
                Seat Numbers: %s
                Total Booking Cost: %s
                """.formatted(
                selectedScreening.getFilm().getTitle(),
                selectedScreening.getScreen().getCinema().getName(),
                selectedScreening.getScreen().getCinema().getCity(),
                selectedScreening.getScreeningDate(),
                selectedScreening.getStartTime(),
                selectedScreening.getScreen().getHallType().getLabel(),
                selectedScreening.getScreen().getScreenNumber(),
                screeningTypeLabel(selectedScreening),
                customerNameField.getValue(),
                customerEmailField.getValue(),
                selectedSeats.size(),
                selectedSeatNumbersText(),
                currentTotalPriceText()
        );
    }

    private Screening findFirstScreeningForSelectedFilm() {
        if (selectedFilmId == null) {
            return null;
        }

        return currentScreenings.stream()
                .filter(screening -> Objects.equals(screening.getFilm().getId(), selectedFilmId))
                .findFirst()
                .orElse(null);
    }

    private String estimateLowestPrice(List<Screening> screenings) {
        return screenings.stream()
                .map(screening -> {
                    List<BookingService.SeatOption> options = bookingService.findSeatOptions(screening.getId());

                    return options.stream()
                            .filter(BookingService.SeatOption::available)
                            .map(option -> pricingService.calculateTicketPrice(screening, option.seat()))
                            .min(BigDecimal::compareTo)
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .map(this::formatMoney)
                .orElse("Sold out");
    }

    private void scrollToShowtimes() {
        showtimeArea.getElement().executeJs(
                "this.scrollIntoView({behavior: 'smooth', block: 'start'});"
        );
    }

    private String buildReceipt(
            Booking booking,
            Screening screening,
            List<Seat> selectedSeats
    ) {
        String seatNumbers = selectedSeats.stream()
                .map(Seat::getSeatNumber)
                .collect(Collectors.joining(", "));

        return """
                Booking Reference: %s
                Film: %s
                Cinema: %s
                City: %s
                Date: %s
                Showing Time: %s
                Hall Type: %s
                Screen: %s
                Number of Tickets: %d
                Seat Numbers: %s
                Total Booking Cost: %s
                Booking Date: %s
                Screening Type: %s
                Status: %s
                """.formatted(
                booking.getBookingReference(),
                screening.getFilm().getTitle(),
                screening.getScreen().getCinema().getName(),
                screening.getScreen().getCinema().getCity(),
                screening.getScreeningDate(),
                screening.getStartTime(),
                screening.getScreen().getHallType().getLabel(),
                screening.getScreen().getScreenNumber(),
                selectedSeats.size(),
                seatNumbers,
                formatMoney(booking.getTotalCost()),
                booking.getBookingDate(),
                screeningTypeLabel(screening),
                booking.getStatus()
        );
    }


    private List<Screening> sortAndRemoveDuplicateShowtimes(List<Screening> screenings) {
        Map<String, Screening> unique = new LinkedHashMap<>();

        screenings.stream()
                .filter(Objects::nonNull)
                .sorted(screeningComparator())
                .forEach(screening -> {
                    String key = screening.getScreen().getId()
                            + "-"
                            + screening.getScreeningDate()
                            + "-"
                            + screening.getStartTime();

                    unique.putIfAbsent(key, screening);
                });

        return unique.values()
                .stream()
                .toList();
    }
    private Comparator<Screening> screeningComparator() {
        return Comparator
                .comparing((Screening screening) -> screening.getScreen().getCinema().getName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(screening -> screening.getScreen().getHallType().getLabel(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Screening::getScreeningDate)
                .thenComparing(Screening::getStartTime)
                .thenComparing(screening -> screening.getScreen().getScreenNumber())
                .thenComparing(Screening::getId);
    }

    private Comparator<BookingService.SeatOption> seatOptionComparator() {
        return Comparator
                .comparingInt((BookingService.SeatOption option) -> rowIndex(option.seat().getSeatNumber()))
                .thenComparingInt(option -> seatNumberValue(option.seat().getSeatNumber()));
    }

    private Comparator<Seat> seatComparator() {
        return Comparator
                .comparingInt((Seat seat) -> rowIndex(seat.getSeatNumber()))
                .thenComparingInt(seat -> seatNumberValue(seat.getSeatNumber()));
    }

    private int rowIndex(String seatNumber) {
        if (seatNumber == null || seatNumber.isBlank()) {
            return 999;
        }

        return Character.toUpperCase(seatNumber.charAt(0)) - 'A';
    }

    private int seatNumberValue(String seatNumber) {
        if (seatNumber == null) {
            return 0;
        }

        String digits = seatNumber.replaceAll("\\D+", "");

        if (digits.isBlank()) {
            return 0;
        }

        return Integer.parseInt(digits);
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.UK).format(amount);
    }

    private String formatRuntime(int durationMinutes) {
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;

        if (hours <= 0) {
            return minutes + " min";
        }

        return hours + "h " + minutes + "m";
    }

    private String formatReleaseDate(LocalDate releaseDate) {
        if (releaseDate == null) {
            return "Available soon";
        }

        return releaseDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK));
    }
}
