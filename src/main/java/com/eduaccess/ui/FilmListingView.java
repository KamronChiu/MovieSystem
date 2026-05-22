package com.eduaccess.ui;

import com.eduaccess.domain.Film;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.service.ScreeningService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "", layout = MainLayout.class)
@PageTitle("HCBS — Films")
public class FilmListingView extends Div implements BeforeEnterObserver {

    private final FilmRepository filmRepository;
    private final CinemaRepository cinemaRepository;
    private final ScreeningService screeningService;

    private final Div tabsContainer = new Div();
    private final Div filmGrid = new Div();

    private final TextField searchField = new TextField();
    private final ComboBox<String> cityFilter = new ComboBox<>();
    private final ComboBox<String> genreFilter = new ComboBox<>();

    private final List<PromoSlide> promoSlides = List.of(
            new PromoSlide(
                    "ZOOTOPIA 2",
                    "Book Tickets",
                    "/images/banners/promo-1.jpg",
                    "Zootopia 2"
            ),
            new PromoSlide(
                    "STAR WARS: THE MANDALORIAN AND GROGU",
                    "Book Tickets",
                    "/images/banners/promo-2.jpg",
                    "Star Wars: The Mandalorian and Grogu"
            ),
            new PromoSlide(
                    "MINIONS",
                    "Book Tickets",
                    "/images/banners/promo-3.jpg",
                    "Minions"
            ),
            new PromoSlide(
                    "ZOOTOPIA 2",
                    "Book Tickets",
                    "/images/banners/promo-4.jpg",
                    "Zootopia 2"
            ),
            new PromoSlide(
                    "ZOOTOPIA 2",
                    "Book Tickets",
                    "/images/banners/promo-5.jpg",
                    "Zootopia 2"
            )
    );

    private List<Film> allFilms = List.of();
    private List<Screening> screeningWindow = List.of();

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

        setWidthFull();
        getStyle()
                .set("background", "#020b1d")
                .set("min-height", "100vh")
                .set("color", "white");

        Div page = new Div();
        page.getStyle()
                .set("max-width", "1320px")
                .set("margin", "0 auto")
                .set("padding", "46px 48px 90px 48px")
                .set("box-sizing", "border-box");

        configureFilters();
        loadData();

        page.add(
                buildPromoCarousel(),
                buildTitleBlock(),
                buildTabsAndFilter(),
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
        }

        if (requestedKeyword != null) {
            searchField.setValue(requestedKeyword);
        }

        if (requestedCity != null && !requestedCity.isBlank()) {
            cityFilter.setValue(requestedCity);
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
        Div carousel = new Div();
        carousel.getStyle()
                .set("position", "relative")
                .set("height", "520px")
                .set("margin", "0 0 72px 0")
                .set("overflow", "hidden")
                .set("background", "#020b1d")
                .set("box-shadow", "0 24px 60px rgba(0,0,0,0.35)");

        Div imageLayer = new Div();
        imageLayer.getElement().setAttribute("data-role", "promo-image");
        imageLayer.getStyle()
                .set("position", "absolute")
                .set("inset", "0")
                .set("background-size", "cover")
                .set("background-position", "center")
                .set("transition", "background-image 0.45s ease-in-out");

        Div content = new Div();
        content.getStyle()
                .set("position", "relative")
                .set("z-index", "2")
                .set("height", "100%")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("justify-content", "center")
                .set("padding", "64px")
                .set("box-sizing", "border-box")
                .set("max-width", "760px");

        H1 title = new H1("");
        title.getElement().setAttribute("data-role", "promo-title");
        title.getStyle()
                .set("margin", "0 0 44px 0")
                .set("font-size", "58px")
                .set("line-height", "0.98")
                .set("font-weight", "950")
                .set("letter-spacing", "0.04em")
                .set("color", "white")
                .set("text-transform", "uppercase");

        Button cta = new Button();
        cta.getElement().setAttribute("data-role", "promo-cta");
        cta.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        cta.getStyle()
                .set("width", "230px")
                .set("height", "54px")
                .set("background", "rgba(2,11,29,0.62)")
                .set("border", "1px solid white")
                .set("border-radius", "0")
                .set("font-size", "20px")
                .set("font-weight", "750")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)");

        content.add(title, cta);

        Div next = carouselArrow("›", "promo-next");
        next.getStyle()
                .set("right", "32px")
                .set("top", "42%");

        Div previous = carouselArrow("‹", "promo-prev");
        previous.getStyle()
                .set("right", "32px")
                .set("top", "60%");

        Div dots = new Div();
        dots.getStyle()
                .set("position", "absolute")
                .set("left", "50%")
                .set("bottom", "22px")
                .set("transform", "translateX(-50%)")
                .set("display", "flex")
                .set("gap", "10px")
                .set("z-index", "3");

        for (int i = 0; i < promoSlides.size(); i++) {
            Span dot = new Span();
            dot.getElement().setAttribute("data-role", "promo-dot");
            dot.getElement().setAttribute("data-index", String.valueOf(i));
            dot.getStyle()
                    .set("width", "9px")
                    .set("height", "9px")
                    .set("border-radius", "50%")
                    .set("background", "rgba(255,255,255,0.45)")
                    .set("display", "inline-block")
                    .set("cursor", "pointer")
                    .set("transition", "all 0.2s ease");

            dots.add(dot);
        }

        carousel.add(imageLayer, content, next, previous, dots);

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
        const slides = %s;
        let index = 0;
        
        const image = host.querySelector('[data-role="promo-image"]');
        const title = host.querySelector('[data-role="promo-title"]');
        const cta = host.querySelector('[data-role="promo-cta"]');
        const next = host.querySelector('[data-role="promo-next"]');
        const previous = host.querySelector('[data-role="promo-prev"]');
        const dots = host.querySelectorAll('[data-role="promo-dot"]');
        
        function renderSlide(targetIndex) {
            index = (targetIndex + slides.length) %% slides.length;
            const slide = slides[index];
        
            image.style.backgroundImage =
                "linear-gradient(90deg, rgba(2,11,29,0.84) 0%%, rgba(2,11,29,0.50) 42%%, rgba(2,11,29,0.10) 100%%), url('" + slide.imageUrl + "')";
        
            title.textContent = slide.title;
            cta.textContent = slide.cta;
            cta.setAttribute("data-booking-url", slide.bookingUrl);
        
            dots.forEach((dot, i) => {
                dot.style.background = i === index ? "#38bdf8" : "rgba(255,255,255,0.45)";
                dot.style.width = i === index ? "26px" : "9px";
                dot.style.borderRadius = i === index ? "999px" : "50%%";
            });
        }
        
        next.onclick = () => renderSlide(index + 1);
        previous.onclick = () => renderSlide(index - 1);
        
        dots.forEach((dot) => {
            dot.onclick = () => renderSlide(Number(dot.getAttribute("data-index")));
        });
        
        cta.onclick = () => {
            const url = cta.getAttribute("data-booking-url") || "/booking";
            window.location.href = url;
        };
        
        if (host.__promoTimer) {
            clearInterval(host.__promoTimer);
        }
        
        host.__promoTimer = setInterval(() => {
            renderSlide(index + 1);
        }, 4500);
        
        renderSlide(0);
        """.formatted(slidesJson));
        });

        return carousel;
    }

    private Div carouselArrow(String symbol, String role) {
        Div arrow = new Div();
        arrow.getElement().setAttribute("data-role", role);

        arrow.getStyle()
                .set("position", "absolute")
                .set("z-index", "4")
                .set("width", "64px")
                .set("height", "64px")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("border", "1px solid rgba(255,255,255,0.75)")
                .set("color", "white")
                .set("font-size", "48px")
                .set("font-weight", "300")
                .set("cursor", "pointer")
                .set("transform", "rotate(45deg)")
                .set("background", "rgba(2,11,29,0.24)")
                .set("line-height", "1");

        Span inner = new Span(symbol);
        inner.getStyle()
                .set("transform", "rotate(-45deg)")
                .set("display", "block");

        arrow.add(inner);

        return arrow;
    }

    private Div buildTitleBlock() {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("text-align", "center")
                .set("margin-bottom", "52px");

        H1 title = new H1("ALL FILMS");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "30px")
                .set("font-weight", "800")
                .set("letter-spacing", "0.12em");

        Div underline = new Div();
        underline.getStyle()
                .set("width", "78px")
                .set("height", "3px")
                .set("background", "#0072ce")
                .set("margin", "18px auto 0 auto")
                .set("box-shadow", "0 0 0 1px rgba(56,189,248,0.35)");

        wrapper.add(title, underline);
        return wrapper;
    }

    private Div buildTabsAndFilter() {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "center")
                .set("border-bottom", "1px solid rgba(255,255,255,0.28)")
                .set("margin-bottom", "38px")
                .set("gap", "24px")
                .set("flex-wrap", "wrap");

        tabsContainer.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "52px")
                .set("min-height", "58px")
                .set("flex-wrap", "wrap");

        Div filterBox = new Div();
        filterBox.getStyle()
                .set("display", "flex")
                .set("align-items", "end")
                .set("gap", "12px")
                .set("flex-wrap", "wrap");

        filterBox.add(searchField, cityFilter, genreFilter, resetButton());

        wrapper.add(tabsContainer, filterBox);

        return wrapper;
    }

    private void configureFilters() {
        searchField.setPlaceholder("Search films or cinemas");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.setWidth("230px");
        searchField.addValueChangeListener(event -> applyFilter());
        styleDarkInput(searchField);

        List<String> cities = cinemaRepository.findAll()
                .stream()
                .map(cinema -> cinema.getCity())
                .filter(city -> city != null && !city.isBlank())
                .distinct()
                .sorted()
                .toList();

        cityFilter.setPlaceholder("City");
        cityFilter.setClearButtonVisible(true);
        cityFilter.setWidth("160px");
        cityFilter.setItems(cities);
        cityFilter.addValueChangeListener(event -> {
            String city = event.getValue();
            VaadinSession.getCurrent().setAttribute("selectedCity", city == null ? "" : city);
            applyFilter();
        });
        styleDarkInput(cityFilter);

        genreFilter.setPlaceholder("Genre");
        genreFilter.setClearButtonVisible(true);
        genreFilter.setWidth("180px");
        genreFilter.addValueChangeListener(event -> applyFilter());
        styleDarkInput(genreFilter);
    }

    private void styleDarkInput(Component component) {
        component.getElement().getStyle()
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.08)")
                .set("--vaadin-input-field-value-color", "white")
                .set("--vaadin-input-field-placeholder-color", "#94a3b8")
                .set("--vaadin-input-field-border-width", "1px")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.25)");
    }

    private Button resetButton() {
        Button resetButton = new Button("Reset", event -> {
            searchField.clear();
            cityFilter.clear();
            genreFilter.clear();
            activeTab = FilmTab.ALL;
            VaadinSession.getCurrent().setAttribute("selectedCity", "");
            renderTabs();
            applyFilter();
        });

        resetButton.getStyle()
                .set("background", "transparent")
                .set("color", "white")
                .set("border", "1px solid rgba(255,255,255,0.45)")
                .set("border-radius", "0")
                .set("height", "38px");

        return resetButton;
    }

    private void loadData() {
        allFilms = filmRepository.findAll();

        screeningWindow = screeningService.findScreeningsBetween(
                LocalDate.now(),
                LocalDate.now().plusDays(7)
        );

        List<String> genres = allFilms.stream()
                .map(Film::getGenre)
                .filter(Objects::nonNull)
                .filter(genre -> !genre.isBlank())
                .distinct()
                .sorted()
                .toList();

        genreFilter.setItems(genres);
    }

    private void renderTabs() {
        tabsContainer.removeAll();

        for (FilmTab tab : FilmTab.values()) {
            tabsContainer.add(tab(tab));
        }
    }

    private Span tab(FilmTab tab) {
        boolean active = activeTab == tab;

        Span tabSpan = new Span(tab.label);
        tabSpan.getStyle()
                .set("font-size", "18px")
                .set("font-weight", "650")
                .set("padding", "18px 0")
                .set("cursor", "pointer")
                .set("color", active ? "#38bdf8" : "white")
                .set("border-bottom", active ? "3px solid #38bdf8" : "3px solid transparent");

        tabSpan.addClickListener(event -> {
            activeTab = tab;
            renderTabs();
            applyFilter();
        });

        return tabSpan;
    }

    private void applyFilter() {
        String keyword = searchField.getValue();
        String selectedCity = cityFilter.getValue();
        String selectedGenre = genreFilter.getValue();

        List<Film> base = filmsForActiveTab();

        List<Film> filtered = base.stream()
                .filter(film -> matchesKeyword(film, keyword))
                .filter(film -> matchesCity(film, selectedCity))
                .filter(film -> matchesGenre(film, selectedGenre))
                .toList();

        renderFilms(filtered);
    }

    private List<Film> filmsForActiveTab() {
        return switch (activeTab) {
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

    private boolean isReleased(Film film) {
        return film.getReleaseDate() == null || !film.getReleaseDate().isAfter(LocalDate.now());
    }

    private boolean hasUpcomingScreening(Film film) {
        return screeningWindow.stream()
                .anyMatch(screening -> Objects.equals(screening.getFilm().getId(), film.getId()));
    }

    private boolean hasRegularUpcomingScreening(Film film) {
        return screeningWindow.stream()
                .filter(screening -> Objects.equals(screening.getFilm().getId(), film.getId()))
                .anyMatch(screening -> screening.getScreeningType() == ScreeningType.REGULAR);
    }

    private boolean hasAdvancePreviewScreening(Film film) {
        return screeningWindow.stream()
                .filter(screening -> Objects.equals(screening.getFilm().getId(), film.getId()))
                .anyMatch(screening -> screening.getScreeningType() == ScreeningType.ADVANCE_PREVIEW);
    }

    private boolean matchesKeyword(Film film, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String value = keyword.toLowerCase();

        boolean filmMatch =
                safe(film.getTitle()).contains(value)
                        || safe(film.getGenre()).contains(value)
                        || safe(film.getActors()).contains(value)
                        || safe(film.getDescription()).contains(value);

        boolean cinemaMatch = screeningWindow.stream()
                .filter(screening -> Objects.equals(screening.getFilm().getId(), film.getId()))
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
                .filter(screening -> Objects.equals(screening.getFilm().getId(), film.getId()))
                .anyMatch(screening -> city.equalsIgnoreCase(screening.getScreen().getCinema().getCity()));
    }

    private boolean matchesGenre(Film film, String genre) {
        if (genre == null || genre.isBlank()) {
            return true;
        }

        return genre.equalsIgnoreCase(film.getGenre());
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private void renderFilms(List<Film> films) {
        filmGrid.removeAll();

        filmGrid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(230px, 1fr))")
                .set("gap", "56px 48px")
                .set("align-items", "start");

        if (films.isEmpty()) {
            Paragraph empty = new Paragraph(emptyMessage());
            empty.getStyle()
                    .set("color", "#cbd5e1")
                    .set("font-size", "18px");

            filmGrid.add(empty);
            return;
        }

        for (Film film : films) {
            filmGrid.add(createFilmCard(film));
        }
    }

    private String emptyMessage() {
        if (activeTab == FilmTab.COMING_SOON) {
            return "No coming-soon films are available in the current sample data.";
        }

        if (activeTab == FilmTab.ADVANCE_BOOKINGS) {
            return "No advance preview or advance booking films match the selected filter.";
        }

        return "No films match the selected filter.";
    }

    private Div createFilmCard(Film film) {
        Div card = new Div();
        card.getStyle()
                .set("position", "relative")
                .set("cursor", "pointer")
                .set("min-width", "0");

        Div posterWrapper = new Div();
        posterWrapper.getStyle()
                .set("position", "relative")
                .set("height", "360px")
                .set("overflow", "hidden")
                .set("background", "linear-gradient(145deg, #111827, #4c1d95)")
                .set("box-shadow", "0 16px 34px rgba(0,0,0,0.34)");

        if (film.getPosterUrl() != null && !film.getPosterUrl().isBlank()) {
            Image poster = new Image(film.getPosterUrl(), film.getTitle());
            poster.setWidthFull();
            poster.setHeightFull();
            poster.getStyle()
                    .set("object-fit", "cover")
                    .set("transition", "transform 0.25s ease");

            posterWrapper.add(poster);
        } else {
            Div placeholder = new Div();
            placeholder.setText(film.getTitle());
            placeholder.getStyle()
                    .set("height", "100%")
                    .set("display", "flex")
                    .set("align-items", "center")
                    .set("justify-content", "center")
                    .set("text-align", "center")
                    .set("padding", "20px")
                    .set("box-sizing", "border-box")
                    .set("font-size", "20px")
                    .set("font-weight", "900")
                    .set("color", "white");

            posterWrapper.add(placeholder);
        }

        Div playCorner = new Div();
        playCorner.getStyle()
                .set("position", "absolute")
                .set("top", "0")
                .set("left", "0")
                .set("width", "54px")
                .set("height", "54px")
                .set("background", "#0072ce")
                .set("clip-path", "polygon(0 0, 100% 0, 100% 72%, 72% 100%, 0 100%)")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center");

        Span playIcon = new Span("▶");
        playIcon.getStyle()
                .set("font-size", "22px")
                .set("margin-left", "2px");

        playCorner.add(playIcon);

        Span bookmark = new Span("▱");
        bookmark.getStyle()
                .set("position", "absolute")
                .set("top", "12px")
                .set("right", "14px")
                .set("font-size", "30px")
                .set("font-weight", "300")
                .set("color", "white");

        posterWrapper.add(playCorner, bookmark);

        H2 title = new H2(film.getTitle().toUpperCase());
        title.getStyle()
                .set("font-size", "30px")
                .set("line-height", "0.95")
                .set("font-weight", "900")
                .set("letter-spacing", "0.02em")
                .set("margin", "-10px 0 8px 0")
                .set("color", "white")
                .set("position", "relative")
                .set("z-index", "2");

        Div meta = new Div();
        meta.getStyle()
                .set("display", "flex")
                .set("gap", "8px")
                .set("flex-wrap", "wrap")
                .set("margin-top", "10px");

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

        Button bookButton = new Button("Book tickets", event ->
                getUI().ifPresent(ui -> ui.navigate(BookingView.class, film.getId()))
        );

        bookButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        bookButton.getStyle()
                .set("margin-top", "14px")
                .set("background", "#0072ce")
                .set("border-radius", "0")
                .set("font-weight", "800")
                .set("width", "150px")
                .set("height", "40px")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)");

        card.add(posterWrapper, title, meta, bookButton);

        return card;
    }

    private Span tag(String text) {
        Span tag = new Span(text == null || text.isBlank() ? "-" : text);

        tag.getStyle()
                .set("font-size", "12px")
                .set("font-weight", "700")
                .set("color", "#dbeafe")
                .set("border", "1px solid rgba(219,234,254,0.55)")
                .set("padding", "4px 7px");

        return tag;
    }


    private Span highlightTag(String text) {
        Span tag = new Span(text == null || text.isBlank() ? "-" : text);

        tag.getStyle()
                .set("font-size", "12px")
                .set("font-weight", "900")
                .set("color", "#fdba74")
                .set("border", "1px solid #fb923c")
                .set("padding", "4px 7px");

        return tag;
    }

    private String bookingUrlForFilmTitle(String filmTitle) {
        if (filmTitle == null || filmTitle.isBlank()) {
            return "/booking";
        }

        return allFilms.stream()
                .filter(film -> filmTitle.equalsIgnoreCase(film.getTitle()))
                .findFirst()
                .map(film -> "/booking/" + film.getId())
                .orElse("/booking");
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