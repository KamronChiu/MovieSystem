package com.eduaccess.ui;

import com.eduaccess.config.WebConfig;
import com.eduaccess.domain.AuditAction;
import com.eduaccess.domain.Film;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.repository.ScreeningRepository;
import com.eduaccess.service.AuditLogService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manager-only page for adding new films to the system.
 * Allows poster image upload, all film metadata input, and shows
 * existing films in a grid. New films appear immediately on the
 * public Films listing page.
 */
@CssImport("./styles/manager-admin-pro.css")
@Route(value = "manager/films", layout = MainLayout.class)
@PageTitle("HCBS — Manage Films")
public class ManagerListingsView extends Div implements BeforeEnterObserver {

    private final LoginService loginService;
    private final FilmRepository filmRepository;
    private final ScreeningRepository screeningRepository;
    private final AuditLogService auditLogService;

    // ── Form fields (matches the public film detail page sections) ──
    private final TextField titleField = new TextField("Title");
    private final MultiSelectComboBox<String> genreField = new MultiSelectComboBox<>("Genre");
    private final ComboBox<String> ageRatingField = new ComboBox<>("Rating");
    private final IntegerField durationField = new IntegerField("Runtime (minutes)");
    private final DatePicker releaseDatePicker = new DatePicker("Release date");
    private final TextField directorsField = new TextField("Directors");
    private final TextField actorsField = new TextField("Cast");
    private final TextArea descriptionField = new TextArea("Synopsis");
    private final TextField contentAdviceField = new TextField("Content advice");

    // Poster upload
    private final MemoryBuffer posterBuffer = new MemoryBuffer();
    private final Upload posterUpload = new Upload(posterBuffer);
    private final Image posterPreview = new Image();
    private String uploadedPosterUrl; // /uploads/posters/xxx.jpg
    private String uploadedFileMime;

    // Action buttons
    private final Button createBtn = new Button("Create film");
    private final Button updateBtn = new Button("Update film");
    private final Button resetBtn = new Button("Reset");

    // Right-side grid
    private final Grid<Film> filmGrid = new Grid<>(Film.class, false);
    private Film selectedFilm;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkManagerAccess(event, loginService);
    }

    public ManagerListingsView(FilmRepository filmRepository,
                               ScreeningRepository screeningRepository,
                               LoginService loginService,
                               AuditLogService auditLogService) {
        this.filmRepository = filmRepository;
        this.screeningRepository = screeningRepository;
        this.loginService = loginService;
        this.auditLogService = auditLogService;

        setWidthFull();
        addClassNames("manager-pro-page", "manager-films-page");
        getStyle()
                .set("min-height", "100vh")
                .set("background", "#020b1d")
                .set("color", "white")
                .set("padding", "44px 48px 90px 48px")
                .set("box-sizing", "border-box");

        Div page = new Div();
        page.addClassName("manager-pro-shell");
        page.getStyle()
                .set("max-width", "1320px")
                .set("margin", "0 auto");

        H1 title = new H1("Manage Films");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "42px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.03em")
                .set("text-transform", "uppercase");

        Paragraph intro = new Paragraph("Add new films with poster, cast, runtime and rating. " +
                "Films saved here become available on the public Films page and can then be scheduled into screenings.");
        intro.getStyle()
                .set("max-width", "820px")
                .set("color", "rgba(255,255,255,0.72)")
                .set("font-size", "16px")
                .set("line-height", "1.7")
                .set("margin", "10px 0 34px 0");

        configureFields();
        configureUpload();
        configureGrid();

        Div layout = new Div();
        layout.addClassNames("manager-pro-layout", "manager-film-layout");
        layout.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "440px 1fr")
                .set("gap", "28px")
                .set("align-items", "stretch");
        layout.add(buildFormCard(), buildGridCard());

        page.add(title, intro, layout);
        add(page);

        refreshGrid();
    }

    private void configureFields() {
        titleField.setPlaceholder("e.g. Call Me By Your Name");
        titleField.setWidthFull();
        styleDarkField(titleField);

        descriptionField.setPlaceholder("Short film synopsis shown on the film detail page...");
        descriptionField.setHeight("110px");
        descriptionField.setWidthFull();
        styleDarkField(descriptionField);

        actorsField.setPlaceholder("e.g. Timoth\u00e9e Chalamet, Armie Hammer, Michael Stuhlbarg");
        actorsField.setWidthFull();
        styleDarkField(actorsField);

        directorsField.setPlaceholder("e.g. Luca Guadagnino");
        directorsField.setWidthFull();
        styleDarkField(directorsField);

        genreField.setItems(List.of(
                "Action", "Adventure", "Animation", "Comedy", "Crime",
                "Documentary", "Drama", "Family", "Fantasy", "Horror",
                "Musical", "Mystery", "Romance", "Sci-Fi", "Thriller", "War"
        ));
        genreField.setAllowCustomValue(true);
        genreField.addCustomValueSetListener(e -> {
            String custom = e.getDetail();
            if (custom != null && !custom.isBlank()) {
                Set<String> next = new LinkedHashSet<>(genreField.getValue());
                next.add(custom.trim());
                genreField.setValue(next);
            }
        });
        genreField.setPlaceholder("Select one or more genres");
        genreField.setWidthFull();
        styleDarkField(genreField);

        ageRatingField.setItems("U", "PG", "12A", "12", "15", "18");
        ageRatingField.setValue("12A");
        ageRatingField.setWidthFull();
        styleDarkField(ageRatingField);

        durationField.setMin(40);
        durationField.setMax(360);
        durationField.setValue(120);
        durationField.setStepButtonsVisible(true);
        durationField.setWidthFull();
        styleDarkField(durationField);

        releaseDatePicker.setValue(LocalDate.now());
        releaseDatePicker.setWidthFull();
        styleDarkField(releaseDatePicker);

        contentAdviceField.setPlaceholder("e.g. Mild violence, occasional strong language");
        contentAdviceField.setWidthFull();
        styleDarkField(contentAdviceField);
    }

    private void configureUpload() {
        posterUpload.setAcceptedFileTypes("image/jpeg", "image/png", "image/jpg", "image/webp");
        posterUpload.setMaxFiles(1);
        posterUpload.setMaxFileSize(10 * 1024 * 1024); // 10 MB
        posterUpload.setDropLabel(new Span("Drop poster image here (jpg, png, webp, ≤10 MB)"));
        posterUpload.addClassName("manager-pro-upload");
        posterUpload.getStyle()
                .set("background", "rgba(255,255,255,0.06)")
                .set("border", "1px dashed rgba(255,255,255,0.30)")
                .set("color", "white");

        posterUpload.addSucceededListener(event -> {
            try {
                String original = event.getFileName();
                String ext = extractExt(original);
                String savedName = UUID.randomUUID().toString().replace("-", "") + ext;

                File posterDir = new File(WebConfig.UPLOAD_ROOT, WebConfig.POSTERS_SUBDIR);
                if (!posterDir.exists()) {
                    posterDir.mkdirs();
                }

                File target = new File(posterDir, savedName);
                try (InputStream in = posterBuffer.getInputStream();
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }

                uploadedPosterUrl = "/uploads/" + WebConfig.POSTERS_SUBDIR + "/" + savedName;
                uploadedFileMime = event.getMIMEType();

                posterPreview.setSrc(uploadedPosterUrl);
                posterPreview.setVisible(true);

                Notification.show("Poster uploaded.");
            } catch (Exception ex) {
                Notification.show("Failed to save poster: " + ex.getMessage());
            }
        });

        posterUpload.addFileRejectedListener(event ->
                Notification.show("File rejected: " + event.getErrorMessage()));

        posterPreview.setVisible(false);
        posterPreview.setWidth("160px");
        posterPreview.getStyle()
                .set("margin-top", "10px")
                .set("border-radius", "8px")
                .set("border", "1px solid rgba(255,255,255,0.20)");
    }

    private String extractExt(String name) {
        if (name == null) return ".jpg";
        int idx = name.lastIndexOf('.');
        if (idx < 0) return ".jpg";
        String ext = name.substring(idx).toLowerCase(Locale.ROOT);
        if (ext.length() > 6) return ".jpg";
        return ext;
    }

    private Div buildFormCard() {
        Div card = darkCard();

        H2 heading = new H2("Add / Edit film");
        heading.getStyle()
                .set("margin", "0 0 8px 0")
                .set("font-size", "24px")
                .set("font-weight", "900");

        Span sectionHint = new Span("Fields below match those shown on the public film detail page.");
        sectionHint.getStyle()
                .set("display", "block")
                .set("font-size", "13px")
                .set("color", "rgba(255,255,255,0.60)")
                .set("margin", "0 0 18px 0");

        // Genre + Rating in one row
        Div twoCol = new Div();
        twoCol.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "12px");
        twoCol.add(genreField, ageRatingField);

        // Runtime + Release date in one row
        Div twoCol2 = new Div();
        twoCol2.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "12px");
        twoCol2.add(durationField, releaseDatePicker);

        Span uploadLabel = new Span("Poster image");
        uploadLabel.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("font-weight", "800")
                .set("color", "rgba(255,255,255,0.82)")
                .set("margin", "14px 0 6px 0");

        styleButton(createBtn, "#0072ce", true);
        createBtn.addClickListener(e -> doCreate());

        styleButton(updateBtn, "#0072ce", true);
        updateBtn.setEnabled(false);
        updateBtn.addClickListener(e -> doUpdate());

        styleSecondaryButton(resetBtn);
        resetBtn.addClickListener(e -> clearForm());

        // Delete button is intentionally omitted - the Existing films grid
        // provides a per-row trash icon that opens a ConfirmDialog, which is
        // the preferred (and only) deletion path.
        HorizontalLayout row1 = new HorizontalLayout(createBtn, updateBtn, resetBtn);
        row1.setSpacing(true);
        row1.getStyle().set("margin-top", "20px");

        // Order matches the public film detail page sections:
        // Title → Genre/Rating → Runtime/Release → Directors → Cast → Synopsis → Content advice → Poster
        card.add(
                heading, sectionHint,
                titleField,
                twoCol,
                twoCol2,
                directorsField,
                actorsField,
                descriptionField,
                contentAdviceField,
                uploadLabel, posterUpload, posterPreview,
                row1
        );
        return card;
    }

    private Div buildGridCard() {
        Div card = darkCard();
        // Make the grid card a flex column. We deliberately DO NOT set
        // height: 100% here - that would override the CSS Grid layout's
        // implicit "align-items: stretch" behaviour and cause the grid card
        // to collapse instead of matching the form card's height.
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("min-height", "0")
                .set("overflow", "hidden");

        H2 heading = new H2("Existing films");
        heading.getStyle()
                .set("margin", "0 0 14px 0")
                .set("font-size", "24px")
                .set("font-weight", "900")
                .set("flex", "0 0 auto");

        Span hint = new Span("Click a row to load it for editing, or use the trash icon to delete directly.");
        hint.getStyle()
                .set("display", "block")
                .set("color", "rgba(255,255,255,0.65)")
                .set("font-size", "13px")
                .set("margin", "0 0 12px 0")
                .set("flex", "0 0 auto");

        // Wrap the grid so it can flex-grow within the card.
        // Use flex-basis: 0 (not auto) so the wrapper takes only the
        // remaining space rather than its content's intrinsic height,
        // which is what makes the wrapper stretch to fill the card.
        Div gridWrapper = new Div(filmGrid);
        gridWrapper.getStyle()
                .set("flex", "1 1 0")
                .set("min-height", "0")
                .set("display", "flex");

        card.add(heading, hint, gridWrapper);
        return card;
    }

    private void configureGrid() {
        filmGrid.addComponentColumn(film -> {
            if (film.getPosterUrl() != null && !film.getPosterUrl().isBlank()) {
                Image img = new Image(film.getPosterUrl(), film.getTitle());
                img.setWidth("46px");
                img.setHeight("64px");
                img.getStyle().set("object-fit", "cover").set("border-radius", "4px");
                return img;
            }
            Span empty = new Span("—");
            empty.getStyle().set("color", "#94a3b8");
            return empty;
        }).setHeader("Poster").setWidth("80px").setFlexGrow(0);

        filmGrid.addColumn(Film::getTitle).setHeader("Title").setSortable(true).setAutoWidth(true);
        filmGrid.addColumn(Film::getGenre).setHeader("Genre").setSortable(true).setAutoWidth(true);
        filmGrid.addColumn(Film::getAgeRating).setHeader("Rating").setAutoWidth(true);
        filmGrid.addColumn(film -> film.getDurationMinutes() + " min").setHeader("Runtime").setAutoWidth(true);
        filmGrid.addColumn(film -> film.getReleaseDate() == null ? "—" : film.getReleaseDate().toString())
                .setHeader("Release").setSortable(true).setAutoWidth(true);

        // Per-row delete shortcut so the user does NOT have to populate the
        // form to delete a film. Clicking the trash icon opens a confirm
        // dialog and removes the film directly.
        filmGrid.addComponentColumn(film -> {
            Button del = new Button(new Icon(VaadinIcon.TRASH));
            del.addThemeVariants(
                    ButtonVariant.LUMO_ICON,
                    ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_ERROR
            );
            del.getElement().setAttribute("aria-label", "Delete " + safe(film.getTitle()));
            del.addClickListener(e -> confirmAndDelete(film));
            return del;
        }).setHeader("Actions").setWidth("90px").setFlexGrow(0);

        filmGrid.asSingleSelect().addValueChangeListener(e -> {
            selectedFilm = e.getValue();
            if (selectedFilm != null) {
                populateForm(selectedFilm);
                updateBtn.setEnabled(true);
            } else {
                updateBtn.setEnabled(false);
            }
        });

        filmGrid.setSizeFull();
        filmGrid.addClassName("manager-pro-grid");
        filmGrid.getStyle()
                .set("background", "transparent")
                .set("border-radius", "14px")
                .set("overflow", "hidden");
    }

    private void confirmAndDelete(Film film) {
        if (film == null) {
            return;
        }
        // Use a repository query instead of touching the LAZY
        // film.getScreenings() collection, which would throw
        // LazyInitializationException in the Vaadin UI thread.
        boolean hasScreenings = film.getId() != null
                && screeningRepository.existsByFilmId(film.getId());
        if (hasScreenings) {
            Notification.show("Cannot delete '" + film.getTitle() + "': it still has scheduled screenings. "
                    + "Remove its screenings first in Manager → Cinemas.");
            return;
        }

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete \"" + film.getTitle() + "\"?");
        dialog.setText("This will remove the film from the system. This action cannot be undone.");
        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                String deletedTitle = film.getTitle();
                Long deletedId = film.getId();
                String deletedGenre = film.getGenre();
                filmRepository.delete(film);

                auditLogService.record(
                        AuditAction.FILM_DELETED,
                        "Film",
                        deletedId,
                        null,
                        deletedTitle,
                        null,
                        null,
                        "Film deleted: " + deletedTitle,
                        "Genre: " + (deletedGenre == null ? "-" : deletedGenre)
                );

                Notification.show("Deleted: " + deletedTitle);
                if (selectedFilm != null && Objects.equals(selectedFilm.getId(), deletedId)) {
                    clearForm();
                }
                refreshGrid();
            } catch (RuntimeException ex) {
                Notification.show("Delete failed: " + ex.getMessage());
            }
        });
        dialog.open();
    }

    private void doCreate() {
        try {
            validateForm();

            Film film = new Film(
                    titleField.getValue().trim(),
                    nullIfBlank(descriptionField.getValue()),
                    nullIfBlank(actorsField.getValue()),
                    nullIfBlank(directorsField.getValue()),
                    joinGenres(genreField.getValue()),
                    ageRatingField.getValue(),
                    durationField.getValue(),
                    releaseDatePicker.getValue(),
                    nullIfBlank(contentAdviceField.getValue()),
                    uploadedPosterUrl
            );

            filmRepository.save(film);

            auditLogService.record(
                    AuditAction.FILM_CREATED,
                    "Film",
                    film.getId(),
                    null,
                    film.getTitle(),
                    null,
                    null,
                    "Film created: " + film.getTitle(),
                    filmAuditDetails(film)
            );

            Notification.show("Film created. It is now visible on the Films page.");
            clearForm();
            refreshGrid();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void doUpdate() {
        try {
            if (selectedFilm == null) {
                Notification.show("Select a film to update.");
                return;
            }
            validateForm();

            selectedFilm.setTitle(titleField.getValue().trim());
            selectedFilm.setDescription(nullIfBlank(descriptionField.getValue()));
            selectedFilm.setActors(nullIfBlank(actorsField.getValue()));
            selectedFilm.setDirectors(nullIfBlank(directorsField.getValue()));
            selectedFilm.setGenre(joinGenres(genreField.getValue()));
            selectedFilm.setAgeRating(ageRatingField.getValue());
            selectedFilm.setDurationMinutes(durationField.getValue());
            selectedFilm.setReleaseDate(releaseDatePicker.getValue());
            selectedFilm.setContentAdvice(nullIfBlank(contentAdviceField.getValue()));
            // Only update poster if a new one was uploaded
            if (uploadedPosterUrl != null && !uploadedPosterUrl.isBlank()) {
                selectedFilm.setPosterUrl(uploadedPosterUrl);
            }

            filmRepository.save(selectedFilm);

            auditLogService.record(
                    AuditAction.FILM_UPDATED,
                    "Film",
                    selectedFilm.getId(),
                    null,
                    selectedFilm.getTitle(),
                    null,
                    null,
                    "Film updated: " + selectedFilm.getTitle(),
                    filmAuditDetails(selectedFilm)
            );

            Notification.show("Film updated.");
            clearForm();
            refreshGrid();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void validateForm() {
        if (titleField.getValue() == null || titleField.getValue().isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (genreField.getValue() == null || genreField.getValue().isEmpty()) {
            throw new IllegalArgumentException("Genre is required.");
        }
        if (ageRatingField.getValue() == null || ageRatingField.getValue().isBlank()) {
            throw new IllegalArgumentException("Age rating is required.");
        }
        if (durationField.getValue() == null || durationField.getValue() < 40) {
            throw new IllegalArgumentException("Duration must be at least 40 minutes.");
        }
    }

    private void populateForm(Film film) {
        titleField.setValue(safe(film.getTitle()));
        descriptionField.setValue(safe(film.getDescription()));
        actorsField.setValue(safe(film.getActors()));
        directorsField.setValue(safe(film.getDirectors()));
        genreField.setValue(splitGenres(film.getGenre()));
        ageRatingField.setValue(film.getAgeRating());
        durationField.setValue(film.getDurationMinutes());
        releaseDatePicker.setValue(film.getReleaseDate());
        contentAdviceField.setValue(safe(film.getContentAdvice()));

        uploadedPosterUrl = film.getPosterUrl();
        if (uploadedPosterUrl != null && !uploadedPosterUrl.isBlank()) {
            posterPreview.setSrc(uploadedPosterUrl);
            posterPreview.setVisible(true);
        } else {
            posterPreview.setVisible(false);
        }
        posterUpload.clearFileList();
    }

    private void clearForm() {
        selectedFilm = null;
        titleField.clear();
        descriptionField.clear();
        actorsField.clear();
        directorsField.clear();
        genreField.clear();
        ageRatingField.setValue("12A");
        durationField.setValue(120);
        releaseDatePicker.setValue(LocalDate.now());
        contentAdviceField.clear();
        uploadedPosterUrl = null;
        uploadedFileMime = null;
        posterPreview.setVisible(false);
        posterPreview.setSrc("");
        posterUpload.clearFileList();
        updateBtn.setEnabled(false);
        filmGrid.asSingleSelect().clear();
    }

    private void refreshGrid() {
        List<Film> films = filmRepository.findAll().stream()
                .sorted(Comparator.comparing(Film::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();
        filmGrid.setItems(films);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * Build a one-line audit detail string for a Film. Kept compact so the
     * value fits comfortably in the Audit Log grid's Details column.
     */
    private String filmAuditDetails(Film film) {
        if (film == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Genre: ").append(safe(film.getGenre()).isEmpty() ? "-" : film.getGenre());
        sb.append("; Rating: ").append(safe(film.getAgeRating()).isEmpty() ? "-" : film.getAgeRating());
        sb.append("; Runtime: ").append(film.getDurationMinutes()).append(" min");
        sb.append("; Release: ")
                .append(film.getReleaseDate() == null ? "-" : film.getReleaseDate().toString());
        return sb.toString();
    }

    /**
     * Join the multi-select genre values into a single comma-separated
     * string for persistence in the existing Film.genre column.
     */
    private String joinGenres(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
    }

    /**
     * Split a stored Film.genre string (e.g. "Family, Adventure") back into
     * the Set form expected by MultiSelectComboBox.
     */
    private Set<String> splitGenres(String genre) {
        if (genre == null || genre.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : genre.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /* ── Style helpers ── */

    private Div darkCard() {
        Div card = new Div();
        card.addClassName("manager-pro-card");
        card.getStyle()
                .set("background", "rgba(255,255,255,0.055)")
                .set("border", "1px solid rgba(255,255,255,0.13)")
                .set("box-shadow", "0 22px 70px rgba(0,0,0,0.32)")
                .set("padding", "24px")
                .set("box-sizing", "border-box");
        return card;
    }

    private void styleButton(Button button, String bg, boolean primary) {
        if (primary) {
            button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        }
        button.getStyle()
                .set("height", "46px")
                .set("background", bg)
                .set("color", "white")
                .set("border-radius", "0")
                .set("font-weight", "900")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)")
                .set("padding", "0 36px 0 30px");
    }

    private void styleSecondaryButton(Button button) {
        button.getStyle()
                .set("height", "42px")
                .set("background", "transparent")
                .set("color", "white")
                .set("border", "1px solid rgba(255,255,255,0.45)")
                .set("border-radius", "0")
                .set("font-weight", "800");
    }

    private void styleDarkField(com.vaadin.flow.component.Component component) {
        component.getElement().getStyle()
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.08)")
                .set("--vaadin-input-field-value-color", "white")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.82)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.50)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.20)")
                .set("--vaadin-input-field-focused-highlight", "#38bdf8")
                .set("color", "white");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Inject chip styles directly into document.head so we don't depend on
        // the Vaadin frontend bundle being rebuilt. The MultiSelectComboBox
        // chip web component lives outside the input field's CSS-variable
        // scope, so the only reliable cross-shadow-DOM way is a global rule.
        attachEvent.getUI().getPage().executeJs(
                "(()=>{const id='hcbs-chip-style';" +
                        "if(document.getElementById(id))return;" +
                        "const s=document.createElement('style');s.id=id;" +
                        "s.textContent=\"vaadin-multi-select-combo-box-chip{" +
                        "background:#2563eb !important;color:#fff !important;" +
                        "border-radius:999px !important;padding:2px 10px !important;" +
                        "margin:2px 4px 2px 0 !important;font-weight:600 !important;" +
                        "display:inline-flex !important;align-items:center !important;" +
                        "min-height:24px !important;}" +
                        "vaadin-multi-select-combo-box-chip::part(label){color:#fff !important;}" +
                        "vaadin-multi-select-combo-box-chip::part(remove-button){color:rgba(255,255,255,0.85) !important;}\";" +
                        "document.head.appendChild(s);})();"
        );
    }
}
