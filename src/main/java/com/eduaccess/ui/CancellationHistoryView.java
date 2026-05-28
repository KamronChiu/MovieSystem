package com.eduaccess.ui;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.CancellationRecord;
import com.eduaccess.service.CancellationService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Cancellation History Dashboard.
 * <p>
 * Lists every booking whose lifecycle has fully reached
 * {@link BookingStatus#REFUNDED}. The dashboard is the read-only
 * counterpart of {@link CancellationView}: while the main page
 * focuses on bookings that still require action, this page is the
 * archive of completed refunds.
 * <p>
 * The visual language mirrors {@link AuditLogView} — dark hero
 * background plus a white master card — so it feels native to the
 * existing back-office surface.
 */
@Route(value = "cancellation-history", layout = MainLayout.class)
@PageTitle("HCBS — Refund History")
public class CancellationHistoryView extends Div implements BeforeEnterObserver {

    private static final String DARK_BG = "#020b1d";
    private static final String BLUE = "#0072ce";
    private static final String LIGHT_TEXT = "#142033";
    private static final String LIGHT_MUTED = "#64748b";
    private static final String CARD_BORDER = "#d8e2ef";

    private static final DateTimeFormatter STAMP_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.UK);
    private static final NumberFormat MONEY_FMT =
            NumberFormat.getCurrencyInstance(Locale.UK);

    private final CancellationService cancellationService;
    private final LoginService loginService;

    private final TextField referenceField = new TextField("Booking Reference");
    private final TextField customerField = new TextField("Customer");
    private final Button refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
    private final Button clearButton = new Button("Clear");

    private final Grid<HistoryRow> grid = new Grid<>(HistoryRow.class, false);
    private final List<HistoryRow> allRows = new ArrayList<>();
    private final ListDataProvider<HistoryRow> dataProvider =
            new ListDataProvider<>(allRows);

    private final Span totalLabel = new Span();

    public CancellationHistoryView(CancellationService cancellationService,
                                   LoginService loginService) {
        this.cancellationService = cancellationService;
        this.loginService = loginService;

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

        container.add(buildHeader(), buildFilterBar(), buildGridCard());
        add(container);

        refreshRows();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Refund History is reserved for the same staff that already see the
        // Cancellation surface, to keep customer details on a need-to-know basis.
        PermissionChecker.checkCancellationAccess(event, loginService);
    }

    // ── Header ────────────────────────────────────────────────────────────

    private Div buildHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("margin-bottom", "28px")
                .set("gap", "16px")
                .set("flex-wrap", "wrap");

        Div left = new Div();

        Div titleRow = new Div();
        titleRow.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "14px");

        Icon titleIcon = new Icon(VaadinIcon.CHECK_CIRCLE);
        titleIcon.setSize("28px");
        titleIcon.getStyle().set("color", "#34d399");

        H2 title = new H2("Refund History");
        title.getStyle()
                .set("margin", "0")
                .set("font-weight", "950")
                .set("letter-spacing", "0.04em");

        titleRow.add(titleIcon, title);

        Span subtitle = new Span(
                "Completed refunds — bookings that have fully reached the REFUNDED state.");
        subtitle.getStyle()
                .set("display", "block")
                .set("color", "#94a3b8")
                .set("font-size", "14px")
                .set("margin-top", "8px");

        left.add(titleRow, subtitle);

        totalLabel.getStyle()
                .set("padding", "8px 16px")
                .set("border-radius", "999px")
                .set("background", "rgba(255,255,255,0.08)")
                .set("border", "1px solid rgba(255,255,255,0.15)")
                .set("font-weight", "800")
                .set("font-size", "14px")
                .set("color", "#dbeafe");

        header.add(left, totalLabel);
        return header;
    }

    // ── Filter bar ────────────────────────────────────────────────────────

    private Div buildFilterBar() {
        Div bar = new Div();
        bar.getStyle()
                .set("background", "white")
                .set("color", LIGHT_TEXT)
                .set("padding", "20px 24px")
                .set("margin-bottom", "24px")
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr 140px 110px")
                .set("gap", "16px")
                .set("align-items", "end")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.3)")
                .set("border-radius", "8px");

        referenceField.setPlaceholder("e.g. HCBS-...");
        referenceField.setWidthFull();
        referenceField.setPrefixComponent(new Icon(VaadinIcon.HASH));
        referenceField.setClearButtonVisible(true);
        referenceField.setValueChangeMode(ValueChangeMode.LAZY);
        referenceField.addValueChangeListener(e -> applyFilters());

        customerField.setPlaceholder("name or email");
        customerField.setWidthFull();
        customerField.setPrefixComponent(new Icon(VaadinIcon.USER));
        customerField.setClearButtonVisible(true);
        customerField.setValueChangeMode(ValueChangeMode.LAZY);
        customerField.addValueChangeListener(e -> applyFilters());

        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refreshButton.getStyle()
                .set("background", BLUE)
                .set("color", "white")
                .set("height", "44px")
                .set("font-weight", "800");
        refreshButton.addClickListener(e -> {
            refreshRows();
            Notification.show("Refund history refreshed",
                            1500, Notification.Position.TOP_END)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        clearButton.getStyle().set("height", "44px");
        clearButton.addClickListener(e -> {
            referenceField.clear();
            customerField.clear();
            applyFilters();
        });

        bar.add(referenceField, customerField, refreshButton, clearButton);
        return bar;
    }

    // ── Grid card ─────────────────────────────────────────────────────────

    private Div buildGridCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", "white")
                .set("color", LIGHT_TEXT)
                .set("border-radius", "8px")
                .set("padding", "1px")
                .set("overflow", "hidden")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.3)")
                .set("border", "1px solid " + CARD_BORDER);

        configureGrid();
        card.add(grid);
        return card;
    }

    private void configureGrid() {
        grid.addColumn(HistoryRow::bookingReference)
                .setHeader("Booking Ref")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing(HistoryRow::bookingReference,
                        Comparator.nullsLast(String::compareToIgnoreCase)));

        grid.addColumn(HistoryRow::customer)
                .setHeader("Customer")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true)
                .setComparator(Comparator.comparing(HistoryRow::customer,
                        Comparator.nullsLast(String::compareToIgnoreCase)));

        grid.addColumn(r -> r.refundAmount() == null
                        ? "—" : MONEY_FMT.format(r.refundAmount()))
                .setHeader("Refund Amount")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        (HistoryRow r) -> r.refundAmount() == null
                                ? BigDecimal.ZERO : r.refundAmount()));

        Grid.Column<HistoryRow> dateCol = grid.addColumn(r ->
                        r.cancelledAt() == null ? "—" : r.cancelledAt().format(STAMP_FMT))
                .setHeader("Refunded At")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing(HistoryRow::cancelledAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        grid.addComponentColumn(this::renderStatusBadge)
                .setHeader("Status")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing(r ->
                        r.status() == null ? "" : r.status().name()));

        grid.setDataProvider(dataProvider);
        grid.setHeight("680px");
        grid.setMultiSort(true);

        // Default ordering: newest refunds first.
        grid.sort(List.of(new GridSortOrder<>(dateCol, SortDirection.DESCENDING)));
    }

    private Span renderStatusBadge(HistoryRow row) {
        BookingStatus status = row.status();
        Span pill = new Span(status == null ? "—" : status.getDisplayName());
        pill.getStyle()
                .set("display", "inline-block")
                .set("padding", "4px 12px")
                .set("border-radius", "999px")
                .set("font-size", "12px")
                .set("font-weight", "800")
                .set("letter-spacing", "0.03em")
                .set("background", status == null ? "#e2e8f0" : status.getBadgeBackground())
                .set("color", status == null ? "#475569" : status.getBadgeTextColor());
        return pill;
    }

    // ── Data + filters ────────────────────────────────────────────────────

    private void refreshRows() {
        allRows.clear();
        cancellationService.findAllBookings().stream()
                .filter(b -> b.getStatus() == BookingStatus.REFUNDED)
                .map(this::toRow)
                .forEach(allRows::add);
        dataProvider.refreshAll();
        applyFilters();
    }

    private HistoryRow toRow(Booking booking) {
        // Pull the persisted cancellation record straight from the service so this
        // view stays aligned with the multi-step refund pipeline restored upstream
        // (CancellationService now exposes findCancellationRecord again).
        CancellationRecord record = cancellationService
                .findCancellationRecord(booking.getBookingReference())
                .orElse(null);
        BigDecimal amount = record != null && record.getRefundAmount() != null
                ? record.getRefundAmount()
                : BigDecimal.ZERO;
        LocalDateTime cancelledAt = record != null ? record.getCancelledAt() : null;

        String name = booking.getCustomerName() == null ? "" : booking.getCustomerName();
        String email = booking.getCustomerEmail() == null ? "" : booking.getCustomerEmail();
        String customer = email.isBlank() ? name : (name + " <" + email + ">");

        return new HistoryRow(
                booking.getBookingReference(),
                customer.trim(),
                amount,
                cancelledAt,
                booking.getStatus()
        );
    }

    private void applyFilters() {
        String ref = referenceField.getValue() == null
                ? "" : referenceField.getValue().trim().toUpperCase();
        String cust = customerField.getValue() == null
                ? "" : customerField.getValue().trim().toLowerCase();

        dataProvider.setFilter(row -> {
            if (!ref.isEmpty()) {
                String r = row.bookingReference();
                if (r == null || !r.toUpperCase().contains(ref)) {
                    return false;
                }
            }
            if (!cust.isEmpty()) {
                String c = row.customer();
                if (c == null || !c.toLowerCase().contains(cust)) {
                    return false;
                }
            }
            return true;
        });

        int visible = dataProvider.size(new com.vaadin.flow.data.provider.Query<>());
        totalLabel.setText(visible + " entries (of " + allRows.size() + ")");
    }

    /**
     * Flat row backing the grid — keeps the view layer free of N+1
     * lookups while sorting/filtering.
     */
    private record HistoryRow(
            String bookingReference,
            String customer,
            BigDecimal refundAmount,
            LocalDateTime cancelledAt,
            BookingStatus status
    ) {
    }
}
