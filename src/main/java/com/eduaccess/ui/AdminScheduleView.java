package com.eduaccess.ui;

import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.Film;
import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.repository.ScreenRepository;
import com.eduaccess.service.LoginService;
import com.eduaccess.service.SchedulingService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@CssImport("./styles/admin-schedule-pro.css")
@Route(value = "admin/schedule", layout = MainLayout.class)
@PageTitle("HCBS — Admin Schedule")
public class AdminScheduleView extends Div implements BeforeEnterObserver {

    private final LoginService loginService;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkAdminAccess(event, loginService);
    }

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.UK);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_SHOWS_PER_SCREEN_PER_DAY = 4;

    private final SchedulingService schedulingService;
    private final FilmRepository filmRepository;
    private final CinemaRepository cinemaRepository;
    private final ScreenRepository screenRepository;
    private final BookingRepository bookingRepository;

    private final ComboBox<Cinema> cinemaBox = new ComboBox<>("Cinema");
    private final ComboBox<Screen> screenBox = new ComboBox<>("Screen");
    private final Div filmList = new Div();
    private final Div timetable = new Div();
    private final Span weekLabel = new Span();
    private final Span pendingLabel = new Span();

    private final Button autoButton = new Button("Auto-fill week");
    private final Button confirmButton = new Button("Confirm changes");
    private final Button discardButton = new Button("Discard changes");

    private final List<ScheduleItem> scheduleItems = new ArrayList<>();
    private final Random random = new Random();

    private LocalDate weekStart = startOfWeek(LocalDate.now());
    private Cinema lastCinema;
    private Screen lastScreen;
    private boolean dirty;
    private boolean changingProgrammatically;
    private long temporaryId = 1;

    public AdminScheduleView(
            SchedulingService schedulingService,
            FilmRepository filmRepository,
            CinemaRepository cinemaRepository,
            ScreenRepository screenRepository,
            BookingRepository bookingRepository,
            LoginService loginService
    ) {
        this.schedulingService = schedulingService;
        this.filmRepository = filmRepository;
        this.cinemaRepository = cinemaRepository;
        this.screenRepository = screenRepository;
        this.bookingRepository = bookingRepository;
        this.loginService = loginService;

        // Initialize styled buttons
        stylePrimaryButton(autoButton);
        stylePrimaryButton(confirmButton);
        styleSecondaryButton(discardButton);

        setWidthFull();
        addClassName("admin-schedule-pro-page");
        getStyle()
                .set("min-height", "100vh")
                .set("background", "#020b1d")
                .set("color", "white")
                .set("padding", "38px 48px 90px 48px")
                .set("box-sizing", "border-box");

        Div page = new Div();
        page.getStyle()
                .set("max-width", "1440px")
                .set("margin", "0 auto");

        H1 title = new H1("Admin Film Scheduler");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "42px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.03em")
                .set("text-transform", "uppercase")
                .set("color", "white");

        configureControls();
        renderFilmList();
        refreshScheduleItems();

        Div app = new Div();
        app.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "300px 1fr")
                .set("gap", "28px")
                .set("align-items", "start");

        app.add(buildFilmPanel(), buildSchedulePanel());
        page.add(title, app);
        add(page);
    }

    private void configureControls() {
        List<Cinema> cinemas = cinemaRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Cinema::getCity).thenComparing(Cinema::getName))
                .toList();

        cinemaBox.setItems(cinemas);
        cinemaBox.setItemLabelGenerator(cinema -> cinema.getCity() + " — " + cinema.getName());
        cinemaBox.setPlaceholder("Select cinema");
        cinemaBox.setWidth("320px");
        styleDarkField(cinemaBox);

        screenBox.setItemLabelGenerator(this::formatScreenLabel);
        screenBox.setPlaceholder("Select screen");
        screenBox.setWidth("230px");
        styleDarkField(screenBox);

        cinemaBox.addValueChangeListener(event -> {
            if (changingProgrammatically) {
                return;
            }

            if (dirty && event.isFromClient()) {
                Notification.show("Please confirm or discard changes before changing cinema.");
                changingProgrammatically = true;
                cinemaBox.setValue(lastCinema);
                changingProgrammatically = false;
                return;
            }

            Cinema cinema = event.getValue();
            lastCinema = cinema;
            updateScreensForCinema(cinema, null);
            refreshScheduleItems();
        });

        screenBox.addValueChangeListener(event -> {
            if (changingProgrammatically) {
                return;
            }

            if (dirty && event.isFromClient()) {
                Notification.show("Please confirm or discard changes before changing screen.");
                changingProgrammatically = true;
                screenBox.setValue(lastScreen);
                changingProgrammatically = false;
                return;
            }

            lastScreen = event.getValue();
            refreshScheduleItems();
        });

        if (!cinemas.isEmpty()) {
            cinemaBox.setValue(cinemas.get(0));
        } else {
            renderTimetable();
        }
    }

    private void updateScreensForCinema(Cinema cinema, Long preferredScreenId) {
        changingProgrammatically = true;
        screenBox.clear();

        if (cinema == null) {
            screenBox.setItems(List.of());
            lastScreen = null;
            changingProgrammatically = false;
            return;
        }

        List<Screen> screens = screenRepository.findByCinemaIdOrderByScreenNumberAsc(cinema.getId());
        screenBox.setItems(screens);

        Screen selected = null;
        if (preferredScreenId != null) {
            selected = screens.stream()
                    .filter(screen -> Objects.equals(screen.getId(), preferredScreenId))
                    .findFirst()
                    .orElse(null);
        }
        if (selected == null && !screens.isEmpty()) {
            selected = screens.get(0);
        }

        if (selected != null) {
            screenBox.setValue(selected);
        }
        lastScreen = selected;
        changingProgrammatically = false;
    }

    private Div buildFilmPanel() {
        Div panel = darkCard();
        panel.getStyle()
                .set("position", "sticky")
                .set("top", "96px")
                .set("max-height", "calc(100vh - 130px)")
                .set("overflow", "auto");

        H2 heading = new H2("Films");
        heading.getStyle()
                .set("margin", "0")
                .set("font-size", "28px")
                .set("font-weight", "950");

        Paragraph hint = new Paragraph("Drag a film onto the grid. Draft conflicts are highlighted before you confirm.");
        hint.getStyle()
                .set("color", "#94a3b8")
                .set("font-size", "15px")
                .set("line-height", "1.6")
                .set("margin", "8px 0 18px 0");

        filmList.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "14px");

        panel.add(heading, hint, filmList);
        return panel;
    }

    private Div buildSchedulePanel() {
        Div panel = darkCard();

        Div top = new Div();
        top.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "end")
                .set("gap", "20px")
                .set("flex-wrap", "wrap")
                .set("margin-bottom", "18px");

        Div filters = new Div();
        filters.getStyle()
                .set("display", "flex")
                .set("gap", "14px")
                .set("align-items", "end")
                .set("flex-wrap", "wrap");
        filters.add(cinemaBox, screenBox);

        Div nav = new Div();
        nav.getStyle()
                .set("display", "flex")
                .set("gap", "12px")
                .set("align-items", "center")
                .set("flex-wrap", "wrap");

        Button previous = secondaryButton("◀ Prev");
        previous.addClickListener(event -> moveWeek(-1));

        weekLabel.getStyle()
                .set("font-size", "20px")
                .set("font-weight", "900")
                .set("min-width", "210px")
                .set("text-align", "center");

        Button next = secondaryButton("Next ▶");
        next.addClickListener(event -> moveWeek(1));

        Button today = secondaryButton("Today");
        today.addClickListener(event -> moveToCurrentWeek());

        autoButton.addClickListener(event -> autoFillCurrentScreenWeek());
        confirmButton.addClickListener(event -> confirmChanges());
        discardButton.addClickListener(event -> discardChanges());

        nav.add(previous, weekLabel, next, today, autoButton, confirmButton, discardButton);
        top.add(filters, nav);

        pendingLabel.getStyle()
                .set("display", "block")
                .set("color", "#94a3b8")
                .set("font-size", "14px")
                .set("font-weight", "800")
                .set("margin", "0 0 14px 0");

        timetable.setWidthFull();
        panel.add(top, pendingLabel, timetable);
        updatePendingStatus();
        return panel;
    }

    private void renderFilmList() {
        filmList.removeAll();

        filmRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Film::getTitle))
                .map(this::filmDragCard)
                .forEach(filmList::add);
    }

    private Component filmDragCard(Film film) {
        Div card = new Div();
        card.getElement().setAttribute("draggable", "true");
        card.getElement().executeJs(
                "this.addEventListener('dragstart', function(e) { "
                        + "e.dataTransfer.setData('text/plain', $0); "
                        + "e.dataTransfer.effectAllowed = 'copy'; "
                        + "});",
                "FILM:" + film.getId()
        );

        card.getStyle()
                .set("background", "#081426")
                .set("border", "1px solid rgba(148,163,184,0.28)")
                .set("border-left", "5px solid #38bdf8")
                .set("padding", "16px")
                .set("box-shadow", "0 14px 30px rgba(0,0,0,0.22)")
                .set("cursor", "grab")
                .set("user-select", "none");

        H3 title = new H3(film.getTitle());
        title.getStyle()
                .set("margin", "0 0 10px 0")
                .set("font-size", "19px")
                .set("font-weight", "950")
                .set("line-height", "1.1")
                .set("color", "white");

        Span meta = new Span(film.getAgeRating() + " · " + formatDuration(film.getDurationMinutes()) + " · " + film.getGenre());
        meta.getStyle()
                .set("font-size", "13px")
                .set("font-weight", "800")
                .set("color", "#cbd5e1");

        card.add(title, meta);
        return card;
    }

    private void refreshScheduleItems() {
        scheduleItems.clear();

        Screen selectedScreen = screenBox.getValue();
        LocalDate weekEnd = weekStart.plusDays(6);

        if (selectedScreen != null) {
            schedulingService.findScreeningsByScreenBetween(
                            selectedScreen.getId(),
                            weekStart,
                            weekEnd
                    )
                    .stream()
                    .filter(screening -> !isHistoricalScreening(screening))
                    .sorted(Comparator.comparing(Screening::getScreeningDate)
                            .thenComparing(Screening::getStartTime)
                            .thenComparing(Screening::getId))
                    .map(ScheduleItem::fromScreening)
                    .forEach(scheduleItems::add);
        }

        dirty = false;
        renderTimetable();
        updatePendingStatus();
    }

    private void renderTimetable() {
        timetable.removeAll();

        LocalDate weekEnd = weekStart.plusDays(6);
        weekLabel.setText(
                weekStart.format(DateTimeFormatter.ofPattern("dd MMM", Locale.UK))
                        + " – "
                        + weekEnd.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK))
        );

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "90px repeat(7, minmax(120px, 1fr))")
                .set("border", "1px solid rgba(148,163,184,0.24)")
                .set("background", "#071225")
                .set("overflow", "hidden");

        grid.add(headerCell("Time"));
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            grid.add(dateHeaderCell(day));
        }

        for (int hour = 8; hour <= 22; hour++) {
            LocalTime slotTime = LocalTime.of(hour, 0);
            grid.add(timeCell(slotTime.format(TIME_FORMAT)));

            for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                LocalDate date = weekStart.plusDays(dayOffset);
                Div cell = dropCell(date, slotTime);

                int currentHour = hour;
                scheduleItems.stream()
                        .filter(item -> !item.deleted)
                        .filter(item -> Objects.equals(item.date, date))
                        .filter(item -> item.startTime.getHour() == currentHour)
                        .sorted(Comparator.comparing(item -> item.startTime))
                        .map(this::scheduleCard)
                        .forEach(cell::add);

                grid.add(cell);
            }
        }

        timetable.add(grid);
        updatePendingStatus();
    }

    private Div scheduleCard(ScheduleItem item) {
        boolean conflict = hasDraftConflict(item);
        long soldSeats = soldSeatsFor(item);
        BigDecimal revenue = revenueFor(item);
        boolean locked = isLockedExistingItem(item);

        Div card = new Div();
        if (!locked) {
            card.getElement().setAttribute("draggable", "true");
            card.getElement().executeJs(
                    "this.addEventListener('dragstart', function(e) { "
                            + "e.dataTransfer.setData('text/plain', $0); "
                            + "e.dataTransfer.effectAllowed = 'move'; "
                            + "}); "
                            + "this.addEventListener('mouseenter', function() { this.style.transform='translateY(-3px)'; }); "
                            + "this.addEventListener('mouseleave', function() { this.style.transform='translateY(0)'; });",
                    "ITEM:" + item.uid
            );
        }

        String border = conflict ? "2px solid #ef4444" : item.isChanged() ? "2px solid #f59e0b" : "1px solid rgba(148,163,184,0.35)";
        String accent = conflict ? "#ef4444" : accentFor(item);

        card.getStyle()
                .set("background", "linear-gradient(180deg, #0b172a 0%, #071225 100%)")
                .set("border", border)
                .set("border-left", "5px solid " + accent)
                .set("box-shadow", conflict ? "0 0 0 1px rgba(239,68,68,0.30), 0 16px 34px rgba(0,0,0,0.35)" : "0 16px 34px rgba(0,0,0,0.28)")
                .set("padding", "10px")
                .set("margin-bottom", "8px")
                .set("position", "relative")
                .set("min-height", "118px")
                .set("cursor", locked ? "not-allowed" : "grab")
                .set("opacity", locked ? "0.82" : "1")
                .set("user-select", "none")
                .set("transition", "transform 0.16s ease, border-color 0.16s ease, box-shadow 0.16s ease")
                .set("border-radius", "10px")
                .set("overflow", "hidden");

        Span title = new Span(item.film.getTitle());
        title.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("font-weight", "950")
                .set("line-height", "1.15")
                .set("padding-right", "58px")
                .set("color", "white");

        Span time = new Span(
                item.startTime.format(TIME_FORMAT)
                        + " - "
                        + item.endTime().format(TIME_FORMAT)
                        + " · "
                        + formatDuration(item.film.getDurationMinutes())
        );
        time.getStyle()
                .set("display", "block")
                .set("font-size", "12px")
                .set("font-weight", "800")
                .set("color", "#cbd5e1")
                .set("margin-top", "5px");

        Span screen = new Span(
                "Screen " + item.screen.getScreenNumber()
                        + " · " + item.screen.getHallType().getLabel()
                        + " · " + formatType(item.screeningType)
        );
        screen.getStyle()
                .set("display", "block")
                .set("font-size", "11px")
                .set("font-weight", "800")
                .set("color", "#94a3b8")
                .set("margin-top", "4px");

        Div stats = new Div();
        stats.getStyle()
                .set("display", "flex")
                .set("gap", "6px")
                .set("flex-wrap", "wrap")
                .set("margin-top", "8px");
        stats.add(metricBadge("Sold " + soldSeats + "/" + item.screen.getCapacity(), "#0ea5e9"));
        stats.add(metricBadge(formatMoney(revenue), "#10b981"));

        Div badges = new Div();
        badges.getStyle()
                .set("display", "flex")
                .set("gap", "6px")
                .set("flex-wrap", "wrap")
                .set("margin-top", "7px");

        if (conflict) {
            badges.add(statusBadge("CONFLICT", "#ef4444", "rgba(239,68,68,0.16)"));
        }
        if (item.isChanged()) {
            badges.add(statusBadge(item.newItem ? "NEW" : "MOVED", "#f59e0b", "rgba(245,158,11,0.16)"));
        }
        if (locked) {
            badges.add(statusBadge("LOCKED", "#94a3b8", "rgba(148,163,184,0.14)"));
        }
        if (!item.screeningType.isRegular()) {
            badges.add(statusBadge("ADVANCE", "#fb923c", "rgba(251,146,60,0.16)"));
        }
        badges.add(statusBadge(item.screeningType.getFormat(), item.screeningType.is3D() ? "#a78bfa" : "#38bdf8", item.screeningType.is3D() ? "rgba(167,139,250,0.18)" : "rgba(56,189,248,0.16)"));

        card.add(title, time, screen, stats, badges);

        Button details = tinyButton("i");
        details.getStyle()
                .set("position", "absolute")
                .set("right", "38px")
                .set("top", "8px");
        details.addClickListener(event -> openDetailsDialog(item));

        Button delete = tinyButton("×");
        delete.getStyle()
                .set("position", "absolute")
                .set("right", "8px")
                .set("top", "8px");
        delete.addClickListener(event -> markDeleted(item));
        delete.setEnabled(!locked);

        Button toggleFormat = tinyButton(item.screeningType.is3D() ? "2D" : "3D");
        toggleFormat.getStyle()
                .set("position", "absolute")
                .set("right", "8px")
                .set("bottom", "8px")
                .set("width", "38px")
                .set("min-width", "38px");
        toggleFormat.addClickListener(event -> toggleFormat(item));
        toggleFormat.setEnabled(!locked);

        card.add(details, delete, toggleFormat);
        return card;
    }


    private Div dropCell(LocalDate date, LocalTime time) {
        Div cell = new Div();
        boolean pastSlot = isPastSlot(date, time);

        cell.getStyle()
                .set("min-height", "128px")
                .set("border-left", "1px solid rgba(148,163,184,0.16)")
                .set("border-top", "1px solid rgba(148,163,184,0.16)")
                .set("padding", "8px")
                .set("box-sizing", "border-box")
                .set("background", pastSlot ? "repeating-linear-gradient(135deg, rgba(71,85,105,0.18) 0, rgba(71,85,105,0.18) 8px, rgba(15,23,42,0.38) 8px, rgba(15,23,42,0.38) 16px)" : "rgba(15,23,42,0.42)")
                .set("opacity", pastSlot ? "0.50" : "1")
                .set("cursor", pastSlot ? "not-allowed" : "default")
                .set("transition", "background 0.18s ease, box-shadow 0.18s ease");

        if (pastSlot) {
            if (time.equals(LocalTime.of(8, 0))) {
                Span label = new Span("PAST");
                label.getStyle()
                        .set("display", "inline-block")
                        .set("font-size", "10px")
                        .set("font-weight", "950")
                        .set("letter-spacing", "0.08em")
                        .set("color", "#94a3b8")
                        .set("border", "1px solid rgba(148,163,184,0.30)")
                        .set("border-radius", "999px")
                        .set("padding", "4px 8px");
                cell.add(label);
            }
            return cell;
        }

        cell.getElement().executeJs(
                "this.addEventListener('dragover', function(e) { "
                        + "e.preventDefault(); "
                        + "this.style.background='rgba(56,189,248,0.13)'; "
                        + "this.style.boxShadow='inset 0 0 0 1px rgba(56,189,248,0.65)'; "
                        + "}); "
                        + "this.addEventListener('dragleave', function(e) { "
                        + "this.style.background='rgba(15,23,42,0.42)'; "
                        + "this.style.boxShadow='none'; "
                        + "}); "
                        + "this.addEventListener('drop', function(e) { "
                        + "e.preventDefault(); "
                        + "this.style.background='rgba(15,23,42,0.42)'; "
                        + "this.style.boxShadow='none'; "
                        + "});"
        );

        cell.getElement().addEventListener("drop", event -> {
            String payload = event.getEventData().getString("event.dataTransfer.getData('text/plain')");
            handleDrop(payload, date, time);
        }).addEventData("event.dataTransfer.getData('text/plain')");

        return cell;
    }


    private void handleDrop(String payload, LocalDate date, LocalTime time) {
        if (payload == null || payload.isBlank()) {
            Notification.show("Nothing was dropped onto the timetable.");
            return;
        }

        Screen selectedScreen = screenBox.getValue();
        if (selectedScreen == null) {
            Notification.show("Please select a screen first.");
            return;
        }

        if (isPastSlot(date, time)) {
            Notification.show("Past dates or times are locked and cannot be scheduled.");
            return;
        }

        try {
            if (payload.startsWith("FILM:")) {
                Long filmId = parseLong(payload.substring("FILM:".length()));
                addDroppedFilm(filmId, selectedScreen, date, time);
                return;
            }

            if (payload.startsWith("ITEM:")) {
                String uid = payload.substring("ITEM:".length());
                moveExistingCard(uid, selectedScreen, date, time);
                return;
            }

            Notification.show("The dropped item could not be recognised.");
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }


    private Span metricBadge(String text, String color) {
        Span badge = new Span(text);
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "4px 7px")
                .set("border", "1px solid " + color)
                .set("background", color + "22")
                .set("color", "#e5f6ff")
                .set("border-radius", "999px")
                .set("font-size", "10px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.03em");
        return badge;
    }

    private Span statusBadge(String text, String color, String background) {
        Span badge = new Span(text);
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "4px 7px")
                .set("border", "1px solid " + color)
                .set("background", background)
                .set("color", color)
                .set("border-radius", "999px")
                .set("font-size", "10px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.08em");
        return badge;
    }

    private long soldSeatsFor(ScheduleItem item) {
        if (item == null || item.screeningId == null || item.newItem) {
            return 0;
        }
        return bookingRepository.countSoldSeatsForScreening(item.screeningId);
    }

    private BigDecimal revenueFor(ScheduleItem item) {
        if (item == null || item.screeningId == null || item.newItem) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = bookingRepository.totalRevenueForScreening(item.screeningId);
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean hasDraftConflict(ScheduleItem item) {
        return conflictMessage(item) != null;
    }

    private String conflictMessage(ScheduleItem item) {
        if (item == null || item.deleted) {
            return null;
        }
        try {
            validateItem(item);
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }

        long sameScreenSameDay = scheduleItems.stream()
                .filter(candidate -> !candidate.deleted)
                .filter(candidate -> Objects.equals(candidate.screenId, item.screenId))
                .filter(candidate -> Objects.equals(candidate.date, item.date))
                .count();
        if (sameScreenSameDay > MAX_SHOWS_PER_SCREEN_PER_DAY) {
            return "This screen has more than four shows on " + item.date + ".";
        }

        return scheduleItems.stream()
                .filter(candidate -> candidate != item)
                .filter(candidate -> !candidate.deleted)
                .filter(candidate -> Objects.equals(candidate.screenId, item.screenId))
                .filter(candidate -> Objects.equals(candidate.date, item.date))
                .filter(candidate -> overlaps(item, candidate))
                .findFirst()
                .map(candidate -> "Overlaps with " + candidate.film.getTitle()
                        + " (" + candidate.startTime.format(TIME_FORMAT)
                        + " - " + candidate.endTime().format(TIME_FORMAT) + ").")
                .orElse(null);
    }

    private String accentFor(ScheduleItem item) {
        if (item.screen != null && item.screen.getHallType() != null) {
            return switch (item.screen.getHallType()) {
                case IMAX -> "#818cf8";
                case PREMIUM -> "#f59e0b";
                default -> item.screeningType != null && item.screeningType.is3D() ? "#a78bfa" : "#38bdf8";
            };
        }
        return item.screeningType != null && item.screeningType.is3D() ? "#a78bfa" : "#38bdf8";
    }

    private void toggleFormat(ScheduleItem item) {
        if (item == null || item.deleted) {
            return;
        }
        if (isLockedExistingItem(item)) {
            Notification.show("This screening is locked because it is historical or already has bookings.");
            return;
        }
        item.screeningType = toggle2D3D(item.screeningType);
        item.dirty = true;
        dirty = true;
        renderTimetable();
        Notification.show("Format changed to " + item.screeningType.getFormat() + ". Click Confirm changes to save.");
    }

    private ScreeningType toggle2D3D(ScreeningType type) {
        if (type == null) {
            return ScreeningType.REGULAR_3D;
        }
        return switch (type) {
            case REGULAR_2D -> ScreeningType.REGULAR_3D;
            case REGULAR_3D -> ScreeningType.REGULAR_2D;
            case ADVANCE_PREVIEW_2D -> ScreeningType.ADVANCE_PREVIEW_3D;
            case ADVANCE_PREVIEW_3D -> ScreeningType.ADVANCE_PREVIEW_2D;
        };
    }

    private void openDetailsDialog(ScheduleItem item) {
        Dialog dialog = new Dialog();
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        dialog.setWidth("480px");

        Div panel = new Div();
        panel.getStyle()
                .set("background", "#071225")
                .set("color", "white")
                .set("padding", "26px")
                .set("box-sizing", "border-box")
                .set("border", "1px solid rgba(148,163,184,0.24)")
                .set("box-shadow", "0 24px 70px rgba(0,0,0,0.45)");

        H2 title = new H2(item.film.getTitle());
        title.getStyle()
                .set("margin", "0 0 12px 0")
                .set("font-size", "28px")
                .set("line-height", "1.08")
                .set("font-weight", "950");

        String conflict = conflictMessage(item);
        if (conflict != null) {
            Div warning = new Div();
            warning.setText("Conflict: " + conflict);
            warning.getStyle()
                    .set("background", "rgba(239,68,68,0.14)")
                    .set("border", "1px solid #ef4444")
                    .set("color", "#fecaca")
                    .set("padding", "12px")
                    .set("border-radius", "12px")
                    .set("font-weight", "800")
                    .set("margin", "0 0 16px 0")
                    .set("line-height", "1.45");
            panel.add(title, warning);
        } else {
            panel.add(title);
        }

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "12px")
                .set("margin", "16px 0");

        grid.add(
                detailTile("Date", item.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK))),
                detailTile("Time", item.startTime.format(TIME_FORMAT) + " - " + item.endTime().format(TIME_FORMAT)),
                detailTile("Cinema", item.screen.getCinema().getName()),
                detailTile("Screen", "Screen " + item.screen.getScreenNumber()),
                detailTile("Hall", item.screen.getHallType().getLabel()),
                detailTile("Type", formatType(item.screeningType)),
                detailTile("Sold seats", soldSeatsFor(item) + " / " + item.screen.getCapacity()),
                detailTile("Revenue", formatMoney(revenueFor(item)))
        );

        Div footer = new Div();
        footer.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("gap", "12px")
                .set("margin-top", "18px")
                .set("flex-wrap", "wrap");

        boolean locked = isLockedExistingItem(item);

        Button switchFormat = secondaryButton(item.screeningType.is3D() ? "Switch to 2D" : "Switch to 3D");
        switchFormat.setEnabled(!locked);
        switchFormat.addClickListener(event -> {
            toggleFormat(item);
            dialog.close();
        });

        Button remove = secondaryButton("Delete draft");
        remove.setEnabled(!locked);
        remove.getStyle().set("border", "1px solid #ef4444").set("color", "#fecaca").set("background", "rgba(239,68,68,0.12)");
        remove.addClickListener(event -> {
            markDeleted(item);
            dialog.close();
        });

        Button close = secondaryButton("Close");
        close.addClickListener(event -> dialog.close());

        footer.add(switchFormat, remove, close);
        panel.add(grid, footer);
        dialog.add(panel);
        dialog.open();
    }

    private Div detailTile(String label, String value) {
        Div tile = new Div();
        tile.getStyle()
                .set("background", "rgba(15,23,42,0.78)")
                .set("border", "1px solid rgba(148,163,184,0.20)")
                .set("padding", "12px")
                .set("border-radius", "14px");

        Span top = new Span(label);
        top.getStyle()
                .set("display", "block")
                .set("color", "#94a3b8")
                .set("font-size", "11px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.08em")
                .set("text-transform", "uppercase")
                .set("margin-bottom", "5px");

        Span bottom = new Span(value == null || value.isBlank() ? "-" : value);
        bottom.getStyle()
                .set("display", "block")
                .set("color", "white")
                .set("font-size", "15px")
                .set("font-weight", "850")
                .set("line-height", "1.3");

        tile.add(top, bottom);
        return tile;
    }

    private String formatMoney(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.UK).format(value == null ? BigDecimal.ZERO : value);
    }

    private void addDroppedFilm(Long filmId, Screen screen, LocalDate date, LocalTime time) {
        if (isPastSlot(date, time)) {
            throw new IllegalArgumentException("Past dates or times are locked and cannot be scheduled.");
        }

        Film film = filmRepository.findById(filmId)
                .orElseThrow(() -> new IllegalArgumentException("Film was not found."));

        ScheduleItem item = ScheduleItem.newItem(
                "NEW-" + temporaryId++,
                film,
                screen,
                date,
                time,
                chooseScreeningType(film, date)
        );

        scheduleItems.add(item);
        dirty = true;
        renderTimetable();
        String conflict = conflictMessage(item);
        Notification.show(conflict == null
                ? "Placed " + film.getTitle() + ". Click Confirm changes to save."
                : "Placed with conflict: " + conflict);
    }

    private void moveExistingCard(String uid, Screen screen, LocalDate date, LocalTime time) {
        ScheduleItem item = scheduleItems.stream()
                .filter(candidate -> Objects.equals(candidate.uid, uid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The timetable card was not found."));

        if (isLockedExistingItem(item)) {
            Notification.show("This screening is locked because it is historical or already has bookings.");
            return;
        }

        if (isPastSlot(date, time)) {
            Notification.show("Past dates or times are locked and cannot be scheduled.");
            return;
        }

        item.screen = screen;
        item.screenId = screen.getId();
        item.date = date;
        item.startTime = time;
        item.screeningType = chooseScreeningType(item.film, date);
        item.dirty = true;

        dirty = true;
        renderTimetable();
        String conflict = conflictMessage(item);
        Notification.show(conflict == null
                ? "Moved " + item.film.getTitle() + ". Click Confirm changes to save."
                : "Moved with conflict: " + conflict);
    }

    private ScreeningType chooseScreeningType(Film film, LocalDate date) {
        if (film.getReleaseDate() != null && date.isBefore(film.getReleaseDate())) {
            return ScreeningType.ADVANCE_PREVIEW_2D;
        }
        return ScreeningType.REGULAR_2D;
    }


    private void autoFillCurrentScreenWeek() {
        Screen selectedScreen = screenBox.getValue();
        if (selectedScreen == null) {
            Notification.show("Please select a screen before using auto-fill.");
            return;
        }

        List<Film> films = filmRepository.findAll();
        if (films.isEmpty()) {
            Notification.show("No films are available for scheduling.");
            return;
        }

        int created = 0;
        int skippedPastDays = 0;
        List<LocalTime> candidateTimes = List.of(
                LocalTime.of(9, 0),
                LocalTime.of(11, 30),
                LocalTime.of(14, 0),
                LocalTime.of(16, 30),
                LocalTime.of(19, 0),
                LocalTime.of(21, 15)
        );

        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = weekStart.plusDays(dayOffset);
            if (date.isBefore(LocalDate.now())) {
                skippedPastDays++;
                continue;
            }

            int existingForDay = countActiveItemsFor(selectedScreen, date);
            int capacityLeft = MAX_SHOWS_PER_SCREEN_PER_DAY - existingForDay;
            if (capacityLeft <= 0) {
                continue;
            }

            int targetAdds = Math.min(capacityLeft, 2 + random.nextInt(2));
            int addedForDay = 0;
            int attempts = 0;

            while (addedForDay < targetAdds && attempts < 80) {
                attempts++;
                Film film = films.get(random.nextInt(films.size()));
                LocalTime startTime = candidateTimes.get(random.nextInt(candidateTimes.size()));

                if (!isAutoSlotAvailable(film, selectedScreen, date, startTime)) {
                    continue;
                }

                ScheduleItem item = ScheduleItem.newItem(
                        "NEW-" + temporaryId++,
                        film,
                        selectedScreen,
                        date,
                        startTime,
                        chooseScreeningType(film, date)
                );

                scheduleItems.add(item);
                created++;
                addedForDay++;
            }
        }

        if (created == 0) {
            Notification.show("Auto-fill could not find any free valid slots for the selected week.");
            return;
        }

        dirty = true;
        renderTimetable();

        String message = "Auto-fill created " + created + " draft listings. You can drag, delete, or confirm them.";
        if (skippedPastDays > 0) {
            message += " Past days were skipped.";
        }
        Notification.show(message);
    }

    private int countActiveItemsFor(Screen screen, LocalDate date) {
        return (int) scheduleItems.stream()
                .filter(item -> !item.deleted)
                .filter(item -> Objects.equals(item.screenId, screen.getId()))
                .filter(item -> Objects.equals(item.date, date))
                .count();
    }

    private boolean isAutoSlotAvailable(Film film, Screen screen, LocalDate date, LocalTime startTime) {
        ScheduleItem candidate = ScheduleItem.newItem(
                "CHECK",
                film,
                screen,
                date,
                startTime,
                chooseScreeningType(film, date)
        );

        try {
            validateItem(candidate);
        } catch (RuntimeException ex) {
            return false;
        }

        if (countActiveItemsFor(screen, date) >= MAX_SHOWS_PER_SCREEN_PER_DAY) {
            return false;
        }

        return scheduleItems.stream()
                .filter(item -> !item.deleted)
                .filter(item -> Objects.equals(item.screenId, screen.getId()))
                .filter(item -> Objects.equals(item.date, date))
                .noneMatch(item -> overlaps(candidate, item));
    }

    private void markDeleted(ScheduleItem item) {
        if (item == null) {
            return;
        }

        if (item.newItem) {
            scheduleItems.remove(item);
        } else {
            if (isLockedExistingItem(item)) {
                Notification.show("This screening is locked because it is historical or already has bookings.");
                return;
            }
            item.deleted = true;
            item.dirty = true;
        }
        dirty = true;
        renderTimetable();
        Notification.show("Listing removed from the draft. Click Confirm changes to save.");
    }

    private void confirmChanges() {
        if (!dirty) {
            Notification.show("There are no changes to confirm.");
            return;
        }

        try {
            validateDraftSchedule();

            int deleted = 0;
            int updated = 0;
            int created = 0;

            for (ScheduleItem item : scheduleItems.stream().filter(item -> item.deleted && !item.newItem).toList()) {
                if (isLockedExistingItem(item)) {
                    Notification.show("A historical or booked screening was removed from draft changes instead of being deleted.");
                    item.deleted = false;
                    item.dirty = false;
                    continue;
                }
                schedulingService.deleteScreening(item.screeningId);
                deleted++;
            }

            for (ScheduleItem item : scheduleItems.stream().filter(item -> !item.deleted && !item.newItem && item.dirty).toList()) {
                if (isLockedExistingItem(item)) {
                    throw new IllegalStateException("This screening is locked because it is historical or already has bookings.");
                }
                schedulingService.updateScreening(
                        item.screeningId,
                        item.filmId,
                        item.screenId,
                        item.date,
                        item.startTime,
                        item.screeningType
                );
                updated++;
            }

            for (ScheduleItem item : scheduleItems.stream().filter(item -> !item.deleted && item.newItem).toList()) {
                schedulingService.createScreening(
                        item.filmId,
                        item.screenId,
                        item.date,
                        item.startTime,
                        item.screeningType
                );
                created++;
            }

            Notification.show(
                    "Changes confirmed. Created: " + created
                            + ", moved: " + updated
                            + ", deleted: " + deleted + "."
            );
            refreshScheduleItems();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void discardChanges() {
        if (!dirty) {
            Notification.show("There are no draft changes to discard.");
            return;
        }
        refreshScheduleItems();
        Notification.show("Draft changes discarded.");
    }

    private void validateDraftSchedule() {
        List<ScheduleItem> activeItems = scheduleItems.stream()
                .filter(item -> !item.deleted)
                .toList();

        for (ScheduleItem item : activeItems) {
            validateItem(item);
        }

        Map<String, List<ScheduleItem>> groupedByScreenAndDate = new HashMap<>();
        for (ScheduleItem item : activeItems) {
            String key = item.screenId + "|" + item.date;
            groupedByScreenAndDate.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        for (List<ScheduleItem> sameScreenSameDate : groupedByScreenAndDate.values()) {
            if (sameScreenSameDate.size() > MAX_SHOWS_PER_SCREEN_PER_DAY) {
                ScheduleItem sample = sameScreenSameDate.get(0);
                throw new IllegalStateException(
                        "Screen " + sample.screen.getScreenNumber()
                                + " already has more than four shows on "
                                + sample.date + "."
                );
            }

            for (int i = 0; i < sameScreenSameDate.size(); i++) {
                for (int j = i + 1; j < sameScreenSameDate.size(); j++) {
                    ScheduleItem first = sameScreenSameDate.get(i);
                    ScheduleItem second = sameScreenSameDate.get(j);

                    if (overlaps(first, second)) {
                        throw new IllegalStateException(
                                "Draft conflict: "
                                        + first.film.getTitle()
                                        + " overlaps with "
                                        + second.film.getTitle()
                                        + " on "
                                        + first.date
                                        + "."
                        );
                    }
                }
            }
        }
    }

    private void validateItem(ScheduleItem item) {
        if (item.film == null) {
            throw new IllegalArgumentException("Film is required.");
        }
        if (item.screen == null) {
            throw new IllegalArgumentException("Screen is required.");
        }
        if (item.date == null) {
            throw new IllegalArgumentException("Screening date is required.");
        }
        if (item.startTime == null) {
            throw new IllegalArgumentException("Start time is required.");
        }
        if (isPastSlot(item.date, item.startTime)) {
            throw new IllegalArgumentException("Cannot schedule a screening in the past.");
        }
        if (item.startTime.isBefore(LocalTime.of(8, 0))) {
            throw new IllegalArgumentException("Screenings cannot start before 08:00.");
        }
        if (!item.endTime().isAfter(item.startTime)) {
            throw new IllegalArgumentException("The screening must finish before midnight: " + item.film.getTitle());
        }
        if (item.endTime().isAfter(LocalTime.of(23, 59))) {
            throw new IllegalArgumentException("Screenings must finish before midnight: " + item.film.getTitle());
        }
        if (item.film.getReleaseDate() != null
                && item.date.isBefore(item.film.getReleaseDate())
                && item.screeningType.isRegular()) {
            throw new IllegalArgumentException(
                    "Regular screenings cannot be scheduled before release date. Use advance preview for "
                            + item.film.getTitle()
                            + "."
            );
        }
    }

    private boolean isHistoricalScreening(Screening screening) {
        if (screening == null || screening.getScreeningDate() == null) {
            return false;
        }

        if (screening.getScreeningDate().isBefore(LocalDate.now())) {
            return true;
        }

        return screening.getScreeningDate().isEqual(LocalDate.now())
                && screening.getEndTime() != null
                && !screening.getEndTime().isAfter(LocalTime.now());
    }

    private boolean isPastSlot(LocalDate date, LocalTime startTime) {
        if (date == null) {
            return false;
        }

        if (date.isBefore(LocalDate.now())) {
            return true;
        }

        return date.isEqual(LocalDate.now())
                && startTime != null
                && startTime.isBefore(LocalTime.now());
    }

    private boolean isLockedExistingItem(ScheduleItem item) {
        if (item == null || item.newItem) {
            return false;
        }

        if (isPastSlot(item.date, item.startTime)) {
            return true;
        }

        return item.screeningId != null && bookingRepository.countSoldSeatsForScreening(item.screeningId) > 0;
    }

    private boolean overlaps(ScheduleItem first, ScheduleItem second) {
        return first.startTime.isBefore(second.endTime())
                && first.endTime().isAfter(second.startTime);
    }

    private void moveWeek(int amount) {
        if (dirty) {
            Notification.show("Please confirm or discard changes before changing week.");
            return;
        }
        weekStart = weekStart.plusWeeks(amount);
        refreshScheduleItems();
    }

    private void moveToCurrentWeek() {
        if (dirty) {
            Notification.show("Please confirm or discard changes before changing week.");
            return;
        }
        weekStart = startOfWeek(LocalDate.now());
        refreshScheduleItems();
    }

    private void updatePendingStatus() {
        long created = scheduleItems.stream().filter(item -> !item.deleted && item.newItem).count();
        long moved = scheduleItems.stream().filter(item -> !item.deleted && !item.newItem && item.dirty).count();
        long deleted = scheduleItems.stream().filter(item -> item.deleted && !item.newItem).count();

        if (!dirty) {
            pendingLabel.setText("No draft changes. Drag a film onto a future time slot or use Auto-fill week.");
            confirmButton.setEnabled(false);
            discardButton.setEnabled(false);
            return;
        }

        pendingLabel.setText(
                "Draft changes not saved — new: " + created
                        + ", moved: " + moved
                        + ", deleted: " + deleted
                        + "."
        );
        confirmButton.setEnabled(true);
        discardButton.setEnabled(true);
    }

    private Div dateHeaderCell(LocalDate day) {
        Div cell = new Div();
        boolean isToday = day.isEqual(LocalDate.now());
        boolean pastDay = day.isBefore(LocalDate.now());
        String text = day.format(DAY_FORMAT);
        cell.setText(isToday ? text + " · TODAY" : pastDay ? text + " · PAST" : text);
        cell.getStyle()
                .set("background", isToday ? "rgba(56,189,248,0.18)" : pastDay ? "rgba(71,85,105,0.24)" : "#101d33")
                .set("border-left", "1px solid rgba(148,163,184,0.18)")
                .set("padding", "14px 10px")
                .set("font-size", "13px")
                .set("font-weight", "950")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.08em")
                .set("color", isToday ? "#7dd3fc" : pastDay ? "#94a3b8" : "#cbd5e1")
                .set("opacity", pastDay ? "0.72" : "1");
        return cell;
    }

    private Div headerCell(String text) {
        Div cell = new Div();
        LocalDate today = LocalDate.now();
        boolean isToday = text.toLowerCase(Locale.UK).contains(today.format(DateTimeFormatter.ofPattern("dd MMM", Locale.UK)).toLowerCase(Locale.UK));
        cell.setText(isToday ? text + " · TODAY" : text);
        cell.getStyle()
                .set("background", isToday ? "rgba(56,189,248,0.18)" : "#101d33")
                .set("border-left", "1px solid rgba(148,163,184,0.18)")
                .set("padding", "14px 10px")
                .set("font-size", "13px")
                .set("font-weight", "950")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.08em")
                .set("color", isToday ? "#7dd3fc" : "#cbd5e1");
        return cell;
    }


    private Div timeCell(String text) {
        Div cell = new Div();
        cell.setText(text);
        cell.getStyle()
                .set("border-top", "1px solid rgba(148,163,184,0.16)")
                .set("padding", "12px 10px")
                .set("font-size", "13px")
                .set("font-weight", "900")
                .set("color", "#94a3b8")
                .set("background", "#0b172a")
                .set("box-sizing", "border-box");
        return cell;
    }


    private static LocalDate startOfWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatScreenLabel(Screen screen) {
        if (screen == null) {
            return "";
        }
        return screen.getHallType().getLabel() + " — Screen " + screen.getScreenNumber() + " — " + screen.getCapacity() + " seats";
    }

    private String formatDuration(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours == 0) {
            return mins + "m";
        }
        return mins == 0 ? hours + "h" : hours + "h " + mins + "m";
    }

    private String formatType(ScreeningType type) {
        if (type == null) return "Regular 2D";
        switch (type) {
            case REGULAR_2D: return "Regular 2D";
            case REGULAR_3D: return "Regular 3D";
            case ADVANCE_PREVIEW_2D: return "Advance Preview 2D";
            case ADVANCE_PREVIEW_3D: return "Advance Preview 3D";
            default: return "Regular 2D";
        }
    }

    private String filmCardColour(Film film) {
        return "#ffffff";
    }

    private Div darkCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", "rgba(7,18,37,0.96)")
                .set("border", "1px solid rgba(148,163,184,0.22)")
                .set("box-shadow", "0 20px 50px rgba(0,0,0,0.32)")
                .set("padding", "24px")
                .set("box-sizing", "border-box")
                .set("border-radius", "18px");
        return card;
    }


    private void stylePrimaryButton(Button button) {
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.getStyle()
                .set("height", "46px")
                .set("background", "#0072ce")
                .set("border-radius", "0")
                .set("font-weight", "900")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)")
                .set("padding", "0 34px 0 28px");
    }

    private void styleSecondaryButton(Button button) {
        button.getStyle()
                .set("height", "42px")
                .set("background", "rgba(15,23,42,0.72)")
                .set("color", "#e5e7eb")
                .set("border", "1px solid rgba(148,163,184,0.32)")
                .set("border-radius", "0")
                .set("font-weight", "800");
    }


    private Button secondaryButton(String text) {
        Button button = new Button(text);
        styleSecondaryButton(button);
        return button;
    }

    private Button tinyButton(String text) {
        Button button = new Button(text);
        button.getStyle()
                .set("width", "24px")
                .set("height", "24px")
                .set("min-width", "24px")
                .set("padding", "0")
                .set("border-radius", "999px")
                .set("background", "rgba(15,23,42,0.88)")
                .set("color", "#e5e7eb")
                .set("border", "1px solid rgba(148,163,184,0.45)")
                .set("font-weight", "900");
        return button;
    }


    private void styleDarkField(Component component) {
        component.getElement().getStyle()
                .set("--vaadin-input-field-background", "rgba(15,23,42,0.90)")
                .set("--vaadin-input-field-value-color", "#e5e7eb")
                .set("--vaadin-input-field-label-color", "#cbd5e1")
                .set("--vaadin-input-field-placeholder-color", "#94a3b8")
                .set("--vaadin-input-field-border-color", "rgba(148,163,184,0.32)")
                .set("--vaadin-input-field-focused-highlight", "#38bdf8")
                .set("color", "#e5e7eb");
    }


    private static class ScheduleItem {
        private final String uid;
        private final Long screeningId;
        private final boolean newItem;

        private Long filmId;
        private Film film;
        private Long screenId;
        private Screen screen;
        private LocalDate date;
        private LocalTime startTime;
        private ScreeningType screeningType;
        private boolean dirty;
        private boolean deleted;

        private ScheduleItem(
                String uid,
                Long screeningId,
                boolean newItem,
                Long filmId,
                Film film,
                Long screenId,
                Screen screen,
                LocalDate date,
                LocalTime startTime,
                ScreeningType screeningType,
                boolean dirty
        ) {
            this.uid = uid;
            this.screeningId = screeningId;
            this.newItem = newItem;
            this.filmId = filmId;
            this.film = film;
            this.screenId = screenId;
            this.screen = screen;
            this.date = date;
            this.startTime = startTime;
            this.screeningType = screeningType == null ? ScreeningType.REGULAR_2D : screeningType;
            this.dirty = dirty;
        }

        private static ScheduleItem fromScreening(Screening screening) {
            return new ScheduleItem(
                    "DB-" + screening.getId(),
                    screening.getId(),
                    false,
                    screening.getFilm().getId(),
                    screening.getFilm(),
                    screening.getScreen().getId(),
                    screening.getScreen(),
                    screening.getScreeningDate(),
                    screening.getStartTime(),
                    screening.getScreeningType(),
                    false
            );
        }

        private static ScheduleItem newItem(
                String uid,
                Film film,
                Screen screen,
                LocalDate date,
                LocalTime startTime,
                ScreeningType screeningType
        ) {
            return new ScheduleItem(
                    uid,
                    null,
                    true,
                    film.getId(),
                    film,
                    screen.getId(),
                    screen,
                    date,
                    startTime,
                    screeningType,
                    true
            );
        }

        private LocalTime endTime() {
            return startTime.plusMinutes(film.getDurationMinutes());
        }

        private boolean isChanged() {
            return newItem || dirty;
        }
    }
}
