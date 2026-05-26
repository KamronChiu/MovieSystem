package com.eduaccess.ui;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.service.CancellationService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Cancellation listing page.
 * <p>
 * This page lists every booking and lets the user pick one to start (or
 * resume) the cancellation / refund flow. It does <strong>not</strong>
 * mutate booking state directly — clicking the action button simply
 * navigates to {@link CancellationStatusesView}, where the step-by-step
 * flow drives all status transitions.
 */
@Route(value = "cancellation", layout = MainLayout.class)
@PageTitle("HCBS — Cancellation")
public class CancellationView extends Div implements BeforeEnterObserver {

    private final LoginService loginService;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkCancellationAccess(event, loginService);
    }

    private static final String DARK_BG = "#020b1d";
    private static final String BLUE = "#0072ce";
    private static final String LIGHT_TEXT = "#142033";
    private static final String LIGHT_MUTED = "#64748b";

    private final CancellationService cancellationService;

    // Search components
    private final TextField searchField = new TextField("Booking Reference");
    private final Button searchButton = new Button("Search");
    private final Button clearButton = new Button("Clear");

    // Master-Detail components
    private final Grid<Booking> bookingGrid = new Grid<>(Booking.class, false);
    private final Div detailsCard = new Div();
    private final Button actionButton = new Button("Request Refund");

    private Booking selectedBooking;
    private final List<Booking> allBookings = new ArrayList<>();
    private ListDataProvider<Booking> dataProvider;

    public CancellationView(CancellationService cancellationService, LoginService loginService) {
        this.cancellationService = cancellationService;
        this.loginService = loginService;

        // Initialize Data Provider with an empty list first
        this.dataProvider = new ListDataProvider<>(allBookings);

        setWidthFull();
        getStyle()
                .set("background", DARK_BG)
                .set("min-height", "100vh")
                .set("color", "white");

        Div container = new Div();
        container.getStyle()
                .set("max-width", "1320px")
                .set("margin", "0 auto")
                .set("padding", "44px 48px")
                .set("box-sizing", "border-box");

        // Title Section
        H2 title = new H2("Booking Cancellation Management");
        title.getStyle().set("margin-top", "0").set("font-weight", "950");
        container.add(title);

        // Search Section
        container.add(buildSearchBar());

        // Content Section (Master-Detail)
        container.add(buildMainContent());

        // Setup Actions: navigate to step-by-step flow (no DB mutation here)
        actionButton.addClickListener(e -> navigateToFlow());

        add(container);

        // Load data initially
        refreshBookings();
    }

    private void refreshBookings() {
        allBookings.clear();
        allBookings.addAll(cancellationService.findAllBookings());
        dataProvider.refreshAll();
    }

    private Div buildSearchBar() {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("background", "white")
                .set("color", LIGHT_TEXT)
                .set("padding", "20px 24px")
                .set("margin-bottom", "40px")
                .set("display", "grid")
                .set("grid-template-columns", "1fr 160px 120px")
                .set("gap", "16px")
                .set("align-items", "end")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.3)");

        searchField.setPlaceholder("Search by reference (e.g. HCBS-...)");
        searchField.setWidthFull();
        searchField.setClearButtonVisible(true);
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> applyFilter());

        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.getStyle().set("background", BLUE).set("height", "44px").set("font-weight", "700");
        searchButton.addClickListener(e -> applyFilter());

        clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        clearButton.getStyle().set("height", "44px");
        clearButton.addClickListener(e -> {
            searchField.clear();
            applyFilter();
        });

        wrapper.add(searchField, searchButton, clearButton);
        return wrapper;
    }

    private Div buildMainContent() {
        Div content = new Div();
        content.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "450px 1fr")
                .set("gap", "32px")
                .set("align-items", "start");

        // Left Side: Grid
        configureGrid();
        Div gridWrapper = new Div(bookingGrid);
        gridWrapper.getStyle()
                .set("background", "white")
                .set("border-radius", "8px")
                .set("padding", "1px")
                .set("overflow", "hidden");
        content.add(gridWrapper);

        // Right Side: Details Card
        configureDetailsCard();
        content.add(detailsCard);

        return content;
    }

    private void configureGrid() {
        bookingGrid.addColumn(Booking::getBookingReference)
                .setHeader("Reference")
                .setAutoWidth(true)
                .setFlexGrow(1);

        bookingGrid.addColumn(b -> {
                    if (b.getScreening() != null && b.getScreening().getFilm() != null) {
                        return b.getScreening().getFilm().getTitle();
                    }
                    return "Unknown Film";
                })
                .setHeader("Film")
                .setAutoWidth(true)
                .setFlexGrow(2);

        bookingGrid.addColumn(b -> b.getStatus().getDisplayName())
                .setHeader("Status")
                .setAutoWidth(true)
                .setFlexGrow(1);

        bookingGrid.setDataProvider(dataProvider);
        bookingGrid.setHeight("650px");
        bookingGrid.addSelectionListener(event -> {
            selectedBooking = event.getFirstSelectedItem().orElse(null);
            updateDetailsCard();
        });
    }

    private void configureDetailsCard() {
        detailsCard.getStyle()
                .set("background", "white")
                .set("color", LIGHT_TEXT)
                .set("padding", "40px")
                .set("min-height", "650px")
                .set("border-radius", "8px")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.3)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("box-sizing", "border-box");

        updateDetailsCard();
    }

    private void updateDetailsCard() {
        try {
            detailsCard.removeAll();

            if (selectedBooking == null) {
                renderPlaceholder();
                return;
            }

            renderBookingDetails();
        } catch (Exception e) {
            showError("Error loading booking details: " + e.getMessage());
            renderPlaceholder();
        }
    }

    private void renderPlaceholder() {
        Div placeholder = new Div();
        placeholder.getStyle()
                .set("height", "100%")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("color", LIGHT_MUTED)
                .set("text-align", "center");

        Icon icon = new Icon(VaadinIcon.INFO_CIRCLE_O);
        icon.setSize("48px");
        icon.getStyle().set("margin-bottom", "16px");

        Span text = new Span("Select a booking from the list to view full details and start the refund flow.");
        text.getStyle().set("max-width", "300px").set("font-style", "italic");

        placeholder.add(icon, text);
        detailsCard.add(placeholder);
    }

    private void renderBookingDetails() {
        H3 detailTitle = new H3("Booking Detailed Information");
        detailTitle.getStyle().set("margin-top", "0").set("color", BLUE).set("font-weight", "900");

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "24px")
                .set("margin", "32px 0");

        String filmTitle = "N/A";
        String cinemaInfo = "N/A";
        if (selectedBooking.getScreening() != null) {
            if (selectedBooking.getScreening().getFilm() != null) {
                filmTitle = selectedBooking.getScreening().getFilm().getTitle();
            }
            if (selectedBooking.getScreening().getScreen() != null && selectedBooking.getScreening().getScreen().getCinema() != null) {
                cinemaInfo = selectedBooking.getScreening().getScreen().getCinema().getName()
                        + " (Screen " + selectedBooking.getScreening().getScreen().getScreenNumber() + ")";
            }
        }

        grid.add(createDetailItem("Film Title", filmTitle));
        grid.add(createDetailItem("Cinema & Screen", cinemaInfo));

        String seats = "None";
        if (selectedBooking.getBookingSeats() != null && !selectedBooking.getBookingSeats().isEmpty()) {
            seats = selectedBooking.getBookingSeats().stream()
                    .filter(bs -> bs.getSeat() != null)
                    .map(bs -> bs.getSeat().getSeatNumber())
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
        grid.add(createDetailItem("Booked Seats", seats));

        grid.add(createDetailItem("Customer Name", selectedBooking.getCustomerName()));
        grid.add(createDetailItem("Customer Email", selectedBooking.getCustomerEmail()));
        grid.add(createStatusBadgeItem("Booking Status", selectedBooking.getStatus()));
        grid.add(createDetailItem("Booking Reference", selectedBooking.getBookingReference()));
        grid.add(createDetailItem("Total Amount Paid", formatMoney(selectedBooking.getTotalCost())));

        // VIP indicator (informational only — modified inside the flow)
        if (selectedBooking.isVip()) {
            grid.add(createVipBadgeItem());
        }

        // Action button — label and enabled state depend on current status.
        // Clicking only navigates to the flow page; no DB mutation here.
        BookingStatus status = selectedBooking.getStatus();
        actionButton.setText(actionLabelFor(status));
        actionButton.setWidthFull();
        actionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        actionButton.getStyle()
                .set("margin-top", "auto")
                .set("height", "50px")
                .set("font-weight", "800")
                .set("background", status.isTerminal() ? "#94a3b8" : BLUE)
                .set("color", "white");
        actionButton.setEnabled(!status.isTerminal());

        detailsCard.add(detailTitle, grid, actionButton);
    }

    private String actionLabelFor(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> "Request Refund";
            case CANCELLED -> "Continue Refund Process";
            case REFUND_PENDING -> "Continue Refund Process";
            case REFUNDED -> "Refund Completed";
        };
    }

    private Div createDetailItem(String label, String value) {
        Div item = new Div();
        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("display", "block")
                .set("font-size", "12px")
                .set("font-weight", "700")
                .set("color", LIGHT_MUTED)
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em")
                .set("margin-bottom", "6px");
        Span valueSpan = new Span(value);
        valueSpan.getStyle()
                .set("font-size", "17px")
                .set("font-weight", "700")
                .set("color", LIGHT_TEXT);
        item.add(labelSpan, valueSpan);
        return item;
    }

    private Div createStatusBadgeItem(String label, BookingStatus status) {
        Div item = new Div();
        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("display", "block")
                .set("font-size", "12px")
                .set("font-weight", "700")
                .set("color", LIGHT_MUTED)
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em")
                .set("margin-bottom", "6px");
        Span badge = new Span(status.getDisplayName());
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "5px 14px")
                .set("border-radius", "999px")
                .set("background", status.getBadgeBackground())
                .set("color", status.getBadgeTextColor())
                .set("font-size", "14px")
                .set("font-weight", "800");
        item.add(labelSpan, badge);
        return item;
    }

    private Div createVipBadgeItem() {
        Div item = new Div();
        Span labelSpan = new Span("Customer Tier");
        labelSpan.getStyle()
                .set("display", "block")
                .set("font-size", "12px")
                .set("font-weight", "700")
                .set("color", LIGHT_MUTED)
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em")
                .set("margin-bottom", "6px");
        Span badge = new Span("VIP");
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "5px 14px")
                .set("border-radius", "999px")
                .set("background", "#7c3aed")
                .set("color", "white")
                .set("font-size", "14px")
                .set("font-weight", "800");
        item.add(labelSpan, badge);
        return item;
    }

    private void applyFilter() {
        String filterText = searchField.getValue().trim().toUpperCase();

        if (filterText.isEmpty()) {
            dataProvider.clearFilters();
        } else {
            dataProvider.setFilter(booking ->
                booking.getBookingReference().toUpperCase().contains(filterText) ||
                booking.getScreening().getFilm().getTitle().toUpperCase().contains(filterText)
            );
        }

        // Auto-select if unique match
        if (dataProvider.size(new com.vaadin.flow.data.provider.Query<>()) == 1) {
            allBookings.stream()
                    .filter(b -> b.getBookingReference().toUpperCase().contains(filterText) ||
                                 b.getScreening().getFilm().getTitle().toUpperCase().contains(filterText))
                    .findFirst()
                    .ifPresent(bookingGrid::select);
        }
    }

    /**
     * Navigates to the step-by-step flow page. The flow page reads the
     * booking's current status from the DB and renders the matching step
     * (CONFIRMED → step 1, CANCELLED → step 2, etc.). No DB mutation
     * happens here.
     */
    private void navigateToFlow() {
        if (selectedBooking == null) {
            return;
        }
        if (selectedBooking.getStatus().isTerminal()) {
            Notification.show("This booking is already fully refunded.",
                            3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            return;
        }
        getUI().ifPresent(ui ->
                ui.navigate("cancellation-statuses/" + selectedBooking.getBookingReference()));
    }

    private void showError(String message) {
        Notification.show(message, 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.UK).format(amount);
    }
}
