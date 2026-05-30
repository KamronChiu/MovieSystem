package com.eduaccess.ui;

import com.eduaccess.domain.Film;
import com.eduaccess.domain.Screening;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.service.ScreeningService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "", layout = MainLayout.class)
@PageTitle("HCBS — Films")
@CssImport("./styles/film-listing-pro.css")
public class FilmListingView extends Div implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);

    private final FilmRepository filmRepository;
    private final CinemaRepository cinemaRepository;
    private final ScreeningService screeningService;

    private final Div tabsContainer = new Div();
    private final Div filmGrid = new Div();
    private final Div resultSummary = new Div();

    private final TextField searchField = new TextField();
    private final ComboBox<String> cityFilter = new ComboBox<>();
    private final ComboBox<String> genreFilter = new ComboBox<>();
    private final DatePicker dateFilter = new DatePicker();

    /*
     * UI-only promotional carousel configuration.
     * The real film data for filtering and booking comes from the database.
     */
    private final List<PromoSlide> promoSlides = List.of(
            new PromoSlide("ZOOTOPIA 2", "Find tickets", "/images/banners/promo-1.jpg", "Zootopia 2"),
            new PromoSlide("STAR WARS: THE MANDALORIAN AND GROGU", "Find tickets", "/images/banners/promo-2.jpg", "Star Wars: The Mandalorian and Grogu"),
            new PromoSlide("MINIONS", "Find tickets", "/images/banners/promo-3.jpg", "Minions"),
            new PromoSlide("ZOOTOPIA 2", "Find tickets", "/images/banners/promo-4.jpg", "Zootopia 2"),
            new PromoSlide("ZOOTOPIA 2", "Find tickets", "/images/banners/promo-5.jpg", "Zootopia 2")
    );

    private List<Film> allFilms = List.of();
    private List<Screening> screeningWindow = List.of();
    private List<Screening> dateScreenings = List.of();

    private FilmTab activeTab = FilmTab.ALL;

    private String requestedKeyword;
    private String requestedCity;

    public FilmListingView(
            FilmRepository filmRepository,
            CinemaRepository cinemaRepository,
            ScreeningService screeningService
    ) {
        this.filmRepository = filmRepository;
        this.cinemaRepository = cinemaRepository;
        this.screeningService = screeningService;

        cls(this, "flp-page");
        setWidthFull();

        Div page = cls(new Div(), "flp-shell");

        configureFilters();
        loadData();

        page.add(
                buildPromoCarousel(),
                buildHeaderAndControls(),
                buildTabsBar(),
                resultSummary,
                filmGrid
        );

        add(page);

        renderTabs();
        applyFilter();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Map<String, List<String>> params = event.getLocation()
                .getQueryParameters()
                .getParameters();

        requestedKeyword = firstQueryValue(params, "q");
        requestedCity = firstQueryValue(params, "city");

        if (requestedCity != null && !requestedCity.isBlank()) {
            VaadinSession.getCurrent().setAttribute("selectedCity", requestedCity);
            cityFilter.setValue(requestedCity);
        }

        if (requestedKeyword != null && !requestedKeyword.isBlank()) {
            searchField.setValue(requestedKeyword);
        }

        applyFilter();
    }

    private String firstQueryValue(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.get(0);
        return value == null || value.isBlank() ? null : value;
    }

    private Div buildPromoCarousel() {
        Div carousel = cls(new Div(), "flp-hero");

        Div imageLayer = cls(new Div(), "flp-hero-image");
        imageLayer.getElement().setAttribute("data-role", "promo-image");

        Div shade = cls(new Div(), "flp-hero-shade");

        Div content = cls(new Div(), "flp-hero-content");

        Span eyebrow = cls(new Span("HORIZON CINEMAS"), "flp-eyebrow");

        H1 title = cls(new H1(""), "flp-hero-title");
        title.getElement().setAttribute("data-role", "promo-title");

        Paragraph copy = cls(new Paragraph(
                "A premium booking experience for current releases, advance previews and city-wide showtimes."
        ), "flp-hero-copy");

        Button cta = cls(new Button(), "flp-hero-cta");
        cta.getElement().setAttribute("data-role", "promo-cta");
        cta.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Div heroStats = cls(new Div(), "flp-hero-stats");
        heroStats.add(
                heroMetric(String.valueOf(allFilms.size()), "Films"),
                heroMetric(String.valueOf(distinctGenres()), "Genres"),
                heroMetric(String.valueOf(distinctCities()), "Cities")
        );

        content.add(eyebrow, title, copy, cta, heroStats);

        Div controls = cls(new Div(), "flp-hero-controls");
        Div previous = carouselArrow("‹", "promo-prev");
        Div next = carouselArrow("›", "promo-next");
        controls.add(previous, next);

        Div dots = cls(new Div(), "flp-hero-dots");
        for (int i = 0; i < promoSlides.size(); i++) {
            Span dot = cls(new Span(), "flp-hero-dot");
            dot.getElement().setAttribute("data-role", "promo-dot");
            dot.getElement().setAttribute("data-index", String.valueOf(i));
            dots.add(dot);
        }

        carousel.add(imageLayer, shade, content, controls, dots);

        carousel.addAttachListener(event -> {
            String slidesJson = promoSlides.stream()
                    .map(slide -> """
                            {
                              "title": "%s",
                              "cta": "%s",
                              "imageUrl": "%s",
                              "bookingUrl": "%s"
                            }
                            """.formatted(
                            jsEscape(slide.title()),
                            jsEscape(slide.cta()),
                            jsEscape(slide.imageUrl()),
                            jsEscape(bookingUrlForFilmTitle(slide.targetFilmTitle()))
                    ))
                    .collect(Collectors.joining(",", "[", "]"));

            carousel.getElement().executeJs("""
                    const host = this;
                    const slides = JSON.parse($0);
                    let index = 0;

                    const image = host.querySelector('[data-role="promo-image"]');
                    const title = host.querySelector('[data-role="promo-title"]');
                    const cta = host.querySelector('[data-role="promo-cta"]');
                    const next = host.querySelector('[data-role="promo-next"]');
                    const previous = host.querySelector('[data-role="promo-prev"]');
                    const dots = host.querySelectorAll('[data-role="promo-dot"]');

                    function renderSlide(targetIndex) {
                        index = (targetIndex + slides.length) % slides.length;
                        const slide = slides[index];
                        image.style.backgroundImage =
                            "linear-gradient(90deg, rgba(4,7,18,0.92) 0%, rgba(4,7,18,0.66) 42%, rgba(4,7,18,0.22) 100%), url('" + slide.imageUrl + "')";
                        title.textContent = slide.title;
                        title.classList.toggle('is-long', slide.title.length > 28);
                        title.classList.toggle('is-ultra', slide.title.length > 42);
                        cta.textContent = slide.cta;
                        cta.setAttribute("data-booking-url", slide.bookingUrl);
                        dots.forEach((dot, i) => dot.classList.toggle('is-active', i === index));
                    }

                    next.onclick = () => renderSlide(index + 1);
                    previous.onclick = () => renderSlide(index - 1);
                    dots.forEach(dot => dot.onclick = () => renderSlide(Number(dot.getAttribute('data-index'))));
                    cta.onclick = () => window.location.href = cta.getAttribute('data-booking-url') || '/booking';

                    if (host.__promoTimer) {
                        clearInterval(host.__promoTimer);
                    }
                    host.__promoTimer = setInterval(() => renderSlide(index + 1), 5200);
                    renderSlide(0);
                    """, slidesJson);
        });

        return carousel;
    }

    private Div heroMetric(String value, String label) {
        Div metric = cls(new Div(), "flp-hero-metric");
        metric.add(cls(new Span(value), "flp-hero-metric-value"), cls(new Span(label), "flp-hero-metric-label"));
        return metric;
    }

    private Div carouselArrow(String symbol, String role) {
        Div arrow = cls(new Div(), "flp-hero-arrow");
        arrow.getElement().setAttribute("data-role", role);
        arrow.add(new Span(symbol));
        return arrow;
    }

    private Div buildHeaderAndControls() {
        Div section = cls(new Div(), "flp-control-panel flp-control-panel-compact");

        Div filters = cls(new Div(), "flp-filters flp-filters-wide");
        filters.add(searchField, cityFilter, genreFilter, dateFilter, todayButton(), thisWeekendButton(), resetButton());

        section.add(filters);
        return section;
    }

    private Div buildTabsBar() {
        Div wrapper = cls(new Div(), "flp-tabs-wrap");
        wrapper.add(tabsContainer);
        return wrapper;
    }

    private void configureFilters() {
        searchField.setPlaceholder("Search film, actor or cinema");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(event -> applyFilter());
        cls(searchField, "flp-search");
        styleGoldInput(searchField);

        List<String> cities = cinemaRepository.findAll()
                .stream()
                .map(cinema -> cinema.getCity())
                .filter(city -> city != null && !city.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        cityFilter.setPlaceholder("City");
        cityFilter.setClearButtonVisible(true);
        cityFilter.setItems(cities);
        cityFilter.addValueChangeListener(event -> {
            String city = event.getValue();
            VaadinSession.getCurrent().setAttribute("selectedCity", city == null ? "" : city);
            applyFilter();
        });
        cls(cityFilter, "flp-filter");
        styleGoldInput(cityFilter);

        genreFilter.setPlaceholder("Genre");
        genreFilter.setClearButtonVisible(true);
        genreFilter.addValueChangeListener(event -> applyFilter());
        cls(genreFilter, "flp-filter");
        styleGoldInput(genreFilter);

        dateFilter.setPlaceholder("Any date");
        dateFilter.setClearButtonVisible(true);
        dateFilter.addValueChangeListener(event -> {
            reloadDateScreenings();
            applyFilter();
        });
        cls(dateFilter, "flp-filter");
        styleGoldInput(dateFilter);
    }

    private void styleGoldInput(Component component) {
        component.getElement().getStyle()
                .set("--vaadin-input-field-background", "rgba(9,14,29,0.78)")
                .set("--vaadin-input-field-value-color", "#f8fafc")
                .set("--vaadin-input-field-placeholder-color", "#9ca3af")
                .set("--vaadin-input-field-border-width", "1px")
                .set("--vaadin-input-field-border-color", "rgba(214,170,66,0.30)");
    }

    private Button todayButton() {
        Button button = secondaryButton("Today");
        button.addClickListener(event -> dateFilter.setValue(LocalDate.now()));
        return button;
    }

    private Button thisWeekendButton() {
        Button button = secondaryButton("This Weekend");
        button.addClickListener(event -> {
            LocalDate today = LocalDate.now();
            int daysUntilSaturday = (DayOfWeek.SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            dateFilter.setValue(today.plusDays(daysUntilSaturday));
        });
        return button;
    }

    private Button resetButton() {
        Button resetButton = secondaryButton("Reset");
        resetButton.addClickListener(event -> {
            searchField.clear();
            cityFilter.clear();
            genreFilter.clear();
            dateFilter.clear();
            activeTab = FilmTab.ALL;
            VaadinSession.getCurrent().setAttribute("selectedCity", "");
            reloadDateScreenings();
            renderTabs();
            applyFilter();
        });
        return resetButton;
    }

    private Button secondaryButton(String text) {
        Button button = cls(new Button(text), "flp-secondary-btn");
        return button;
    }

    private void loadData() {
        allFilms = filmRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Film::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();

        reloadScreeningWindow();
        reloadDateScreenings();

        List<String> genres = allFilms.stream()
                .map(Film::getGenre)
                .filter(Objects::nonNull)
                .filter(genre -> !genre.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        genreFilter.setItems(genres);
    }

    private void reloadScreeningWindow() {
        LocalDate today = LocalDate.now();
        screeningWindow = screeningService.findScreeningsBetween(today, today.plusMonths(6));
    }

    private void reloadDateScreenings() {
        LocalDate selectedDate = dateFilter.getValue();
        dateScreenings = selectedDate == null
                ? List.of()
                : screeningService.findScreeningsBetween(selectedDate, selectedDate);
    }

    private void renderTabs() {
        tabsContainer.removeAll();
        cls(tabsContainer, "flp-tabs");
        for (FilmTab tab : FilmTab.values()) {
            tabsContainer.add(tab(tab));
        }
    }

    private Div tab(FilmTab tab) {
        boolean active = activeTab == tab;
        Div tabItem = cls(new Div(), "flp-tab");
        if (active) {
            cls(tabItem, "is-active");
        }

        Span label = cls(new Span(tab.label), "flp-tab-label");
        Span count = cls(new Span(String.valueOf(filmsForTab(tab).size())), "flp-tab-count");
        tabItem.add(label, count);
        tabItem.addClickListener(event -> {
            activeTab = tab;
            renderTabs();
            applyFilter();
        });
        return tabItem;
    }

    private void applyFilter() {
        String keyword = searchField.getValue();
        String selectedCity = cityFilter.getValue();
        String selectedGenre = genreFilter.getValue();

        List<Film> filtered = filmsForActiveTab().stream()
                .filter(this::matchesSelectedDate)
                .filter(film -> matchesKeyword(film, keyword))
                .filter(film -> matchesCity(film, selectedCity))
                .filter(film -> matchesGenre(film, selectedGenre))
                .toList();

        renderResultSummary(filtered.size());
        renderFilms(filtered);
    }

    private List<Film> filmsForActiveTab() {
        return filmsForTab(activeTab);
    }

    private List<Film> filmsForTab(FilmTab tab) {
        return switch (tab) {
            case ALL -> allFilms;
            case NOW_SHOWING -> allFilms.stream()
                    .filter(this::isReleased)
                    .filter(this::hasRegularUpcomingScreening)
                    .toList();
            case ADVANCE_BOOKINGS -> allFilms.stream()
                    .filter(film -> hasAdvancePreviewScreening(film)
                            || (!isReleased(film) && hasUpcomingScreening(film)))
                    .toList();
            case COMING_SOON -> allFilms.stream()
                    .filter(film -> !isReleased(film))
                    .filter(film -> !hasUpcomingScreening(film))
                    .toList();
        };
    }

    private void renderResultSummary(int count) {
        resultSummary.removeAll();
        cls(resultSummary, "flp-result-summary");

        String dateText = dateFilter.getValue() == null
                ? "Any date"
                : dateFilter.getValue().format(DATE_LABEL);
        String cityText = cityFilter.getValue() == null || cityFilter.getValue().isBlank()
                ? "All cities"
                : cityFilter.getValue();

        resultSummary.add(
                cls(new Span(count + " film" + (count == 1 ? "" : "s") + " found"), "flp-result-main"),
                cls(new Span(activeTab.label + " · " + cityText + " · " + dateText), "flp-result-sub")
        );
    }

    private boolean isReleased(Film film) {
        return film.getReleaseDate() == null || !film.getReleaseDate().isAfter(LocalDate.now());
    }

    private boolean hasUpcomingScreening(Film film) {
        return screeningWindow.stream()
                .anyMatch(screening -> sameFilm(screening, film));
    }

    private boolean hasRegularUpcomingScreening(Film film) {
        return screeningWindow.stream()
                .filter(screening -> sameFilm(screening, film))
                .anyMatch(screening -> screening.getScreeningType() != null && screening.getScreeningType().isRegular());
    }

    private boolean hasAdvancePreviewScreening(Film film) {
        return screeningWindow.stream()
                .filter(screening -> sameFilm(screening, film))
                .anyMatch(screening -> screening.getScreeningType() != null && !screening.getScreeningType().isRegular());
    }

    private boolean matchesSelectedDate(Film film) {
        if (dateFilter.getValue() == null) {
            return true;
        }
        return dateScreenings.stream().anyMatch(screening -> sameFilm(screening, film));
    }

    private boolean matchesKeyword(Film film, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String value = keyword.toLowerCase(Locale.ROOT).trim();

        boolean filmMatch =
                safe(film.getTitle()).contains(value)
                        || safe(film.getGenre()).contains(value)
                        || safe(film.getActors()).contains(value)
                        || safe(film.getDirectors()).contains(value)
                        || safe(film.getDescription()).contains(value);

        boolean cinemaMatch = screeningWindow.stream()
                .filter(screening -> sameFilm(screening, film))
                .anyMatch(screening ->
                        safe(screening.getScreen().getCinema().getName()).contains(value)
                                || safe(screening.getScreen().getCinema().getCity()).contains(value)
                );

        return filmMatch || cinemaMatch;
    }

    private boolean matchesCity(Film film, String city) {
        if (city == null || city.isBlank()) {
            return true;
        }

        return screeningWindow.stream()
                .filter(screening -> sameFilm(screening, film))
                .anyMatch(screening -> city.equalsIgnoreCase(screening.getScreen().getCinema().getCity()));
    }

    private boolean matchesGenre(Film film, String genre) {
        return genre == null || genre.isBlank() || genre.equalsIgnoreCase(film.getGenre());
    }

    private boolean sameFilm(Screening screening, Film film) {
        return screening != null
                && screening.getFilm() != null
                && film != null
                && Objects.equals(screening.getFilm().getId(), film.getId());
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void renderFilms(List<Film> films) {
        filmGrid.removeAll();
        cls(filmGrid, "flp-film-grid");

        if (films.isEmpty()) {
            filmGrid.add(buildEmptyState());
            return;
        }

        for (Film film : films) {
            filmGrid.add(createFilmCard(film));
        }
    }

    private Div buildEmptyState() {
        Div empty = cls(new Div(), "flp-empty");
        empty.add(
                cls(new Span("No matching films"), "flp-empty-title"),
                cls(new Paragraph(emptyMessage()), "flp-empty-copy"),
                resetButton()
        );
        return empty;
    }

    private String emptyMessage() {
        if (activeTab == FilmTab.COMING_SOON) {
            return "No coming-soon films are available for the selected filters.";
        }
        if (activeTab == FilmTab.ADVANCE_BOOKINGS) {
            return "No advance preview or advance booking films match the selected filters.";
        }
        return "Try changing the city, genre, search term or date filter.";
    }

    private Div createFilmCard(Film film) {
        Div card = cls(new Div(), "flp-film-card");

        Div posterWrapper = cls(new Div(), "flp-poster-wrap");
        if (film.getPosterUrl() != null && !film.getPosterUrl().isBlank()) {
            Image poster = cls(new Image(film.getPosterUrl(), film.getTitle()), "flp-poster");
            poster.setWidthFull();
            poster.setHeightFull();
            posterWrapper.add(poster);
        } else {
            Div placeholder = cls(new Div(), "flp-poster-placeholder");
            placeholder.setText(film.getTitle());
            posterWrapper.add(placeholder);
        }

        Div posterOverlay = cls(new Div(), "flp-poster-overlay");
        Div playCorner = cls(new Div(), "flp-play-corner");
        playCorner.add(new Span("▶"));

        Span availability = cls(new Span(availabilityLabel(film)), "flp-availability");
        posterWrapper.add(posterOverlay, playCorner, availability);

        Div body = cls(new Div(), "flp-card-body");

        H2 title = cls(new H2(film.getTitle()), "flp-film-title");

        Div meta = cls(new Div(), "flp-meta");
        meta.add(
                tag(film.getAgeRating()),
                tag(film.getGenre()),
                tag(film.getDurationMinutes() + " min")
        );

        if (hasAdvancePreviewScreening(film)) {
            meta.add(highlightTag("Advance Preview"));
        } else if (!isReleased(film) && hasUpcomingScreening(film)) {
            meta.add(highlightTag("Advance Booking"));
        }

        Paragraph description = cls(new Paragraph(shortDescription(film)), "flp-film-description");

        Div details = cls(new Div(), "flp-film-details");
        details.add(
                detailLine("Release", releaseLabel(film)),
                detailLine("Next show", nextShowLabel(film))
        );

        Button bookButton = cls(new Button("Find tickets"), "flp-book-btn");
        bookButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        bookButton.addClickListener(event -> getUI().ifPresent(ui -> ui.getPage().setLocation(bookingUrlForFilm(film))));

        body.add(title, meta, description, details, bookButton);
        card.add(posterWrapper, body);
        return card;
    }

    private String availabilityLabel(Film film) {
        if (hasRegularUpcomingScreening(film)) {
            return "Now showing";
        }
        if (hasAdvancePreviewScreening(film) || (!isReleased(film) && hasUpcomingScreening(film))) {
            return "Advance booking";
        }
        return "Coming soon";
    }

    private String shortDescription(Film film) {
        String description = film.getDescription();
        if (description == null || description.isBlank()) {
            return "Film information will be updated soon.";
        }
        return description.length() <= 120 ? description : description.substring(0, 117).trim() + "...";
    }

    private String releaseLabel(Film film) {
        if (film.getReleaseDate() == null) {
            return "Available";
        }
        return film.getReleaseDate().format(DATE_LABEL);
    }

    private String nextShowLabel(Film film) {
        Optional<LocalDate> next = screeningService.findEarliestUpcomingDateForFilm(film.getId());
        return next.map(localDate -> localDate.format(DATE_LABEL)).orElse("No scheduled shows");
    }

    private Div detailLine(String label, String value) {
        Div row = cls(new Div(), "flp-detail-line");
        row.add(cls(new Span(label), "flp-detail-label"), cls(new Span(value), "flp-detail-value"));
        return row;
    }

    private Span tag(String text) {
        return cls(new Span(text == null || text.isBlank() ? "-" : text), "flp-tag");
    }

    private Span highlightTag(String text) {
        return cls(new Span(text == null || text.isBlank() ? "-" : text), "flp-tag", "flp-tag-gold");
    }

    private String bookingUrlForFilm(Film film) {
        if (film == null || film.getId() == null) {
            return "/booking";
        }

        LocalDate preferredDate = dateFilter.getValue();
        LocalDate earliestDate = screeningService.findEarliestUpcomingDateForFilm(film.getId())
                .orElse(preferredDate == null ? LocalDate.now() : preferredDate);

        StringBuilder url = new StringBuilder("/booking/")
                .append(film.getId())
                .append("?date=")
                .append(earliestDate);

        String selectedCity = cityFilter.getValue();
        screeningWindow.stream()
                .filter(screening -> sameFilm(screening, film))
                .filter(screening -> selectedCity == null
                        || selectedCity.isBlank()
                        || selectedCity.equalsIgnoreCase(screening.getScreen().getCinema().getCity()))
                .findFirst()
                .ifPresent(screening -> url.append("&cinemaId=").append(screening.getScreen().getCinema().getId()));

        return url.toString();
    }

    private String bookingUrlForFilmTitle(String filmTitle) {
        if (filmTitle == null || filmTitle.isBlank()) {
            return "/booking";
        }
        return allFilms.stream()
                .filter(film -> filmTitle.equalsIgnoreCase(film.getTitle()))
                .findFirst()
                .map(this::bookingUrlForFilm)
                .orElse("/booking");
    }

    private int distinctGenres() {
        Set<String> genres = allFilms.stream()
                .map(Film::getGenre)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        return genres.size();
    }

    private int distinctCities() {
        Set<String> cities = cinemaRepository.findAll().stream()
                .map(cinema -> cinema.getCity())
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        return cities.size();
    }

    private String jsEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private <T extends Component & HasStyle> T cls(T component, String... classNames) {
        for (String className : classNames) {
            if (className == null || className.isBlank()) {
                continue;
            }
            for (String token : className.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    component.addClassName(token);
                }
            }
        }
        return component;
    }

    private record PromoSlide(String title, String cta, String imageUrl, String targetFilmTitle) {
    }

    private enum FilmTab {
        ALL("All"),
        NOW_SHOWING("Now showing"),
        COMING_SOON("Coming soon"),
        ADVANCE_BOOKINGS("Advance bookings");

        private final String label;

        FilmTab(String label) {
            this.label = label;
        }
    }
}
