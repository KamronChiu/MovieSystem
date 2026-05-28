package com.eduaccess.ui;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingSeat;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.service.CancellationService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.WildcardParameter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

@Route(value = "cancellation-statuses", layout = MainLayout.class)
@PageTitle("HCBS — Cancellation Status")
public class CancellationStatusesView extends Div
        implements HasUrlParameter<String>, BeforeEnterObserver {

    private final LoginService loginService;
    private final CancellationService cancellationService;

    private static final String BLUE = "#0072ce";
    private static final String LIGHT_BG = "#f4f7fb";
    private static final String LIGHT_PANEL = "#ffffff";
    private static final String LIGHT_PANEL_SOFT = "#eef4fb";
    private static final String LIGHT_TEXT = "#142033";
    private static final String LIGHT_MUTED = "#64748b";
    private static final String LIGHT_BORDER = "#d8e2ef";
    private static final String GREEN = "#10b981";
    private static final String RED = "#dc2626";

    private static final BigDecimal CANCELLATION_RATE = new BigDecimal("0.50");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.UK);

    private String bookingReference;
    private Booking currentBooking;
    private BigDecimal lastCancellationCharge;
    private BigDecimal lastRefundAmount;

    private final Div stepIndicatorArea = new Div();
    private final Div contentArea = new Div();

    public CancellationStatusesView(
            CancellationService cancellationService,
            LoginService loginService
    ) {
        this.cancellationService = cancellationService;
        this.loginService = loginService;

        setWidthFull();
        getStyle()
                .set("background", LIGHT_BG)
                .set("min-height", "100vh")
                .set("color", LIGHT_TEXT);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkCancellationAccess(event, loginService);
    }

    @Override
    public void setParameter(BeforeEvent event, @WildcardParameter String ref) {
        this.bookingReference = ref == null ? "" : ref.trim().toUpperCase();
        loadBookingAndRender();
    }

    private void loadBookingAndRender() {
        removeAll();

        if (bookingReference == null || bookingReference.isBlank()) {
            add(buildErrorView("No booking reference provided."));
            return;
        }

        currentBooking = cancellationService.findBookingByReference(bookingReference)
                .orElse(null);

        if (currentBooking == null) {
            add(buildErrorView("Booking not found: " + bookingReference));
            return;
        }

        lastCancellationCharge = estimateCancellationCharge(currentBooking);
        lastRefundAmount = estimateRefundAmount(currentBooking);

        Div page = new Div();
        page.getStyle()
                .set("max-width", "960px")
                .set("margin", "0 auto")
                .set("padding", "44px 48px 80px 48px")
                .set("box-sizing", "border-box");

        page.add(buildHeader(), stepIndicatorArea, contentArea);
        add(page);

        renderStatusPage();
    }

    private Div buildHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("gap", "16px")
                .set("margin-bottom", "28px");

        Div left = new Div();

        H2 title = new H2("CANCELLATION STATUS");
        title.getStyle()
                .set("font-size", "32px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.04em")
                .set("margin", "0 0 6px 0")
                .set("color", LIGHT_TEXT);

        Span ref = new Span("Booking: " + bookingReference);
        ref.getStyle()
                .set("color", LIGHT_MUTED)
                .set("font-size", "15px")
                .set("font-weight", "700");

        left.add(title, ref);

        Button back = secondaryButton("Back to Cancellation", event ->
                getUI().ifPresent(ui -> ui.navigate("cancellation")));

        header.add(left, back);
        return header;
    }

    private void renderStatusPage() {
        stepIndicatorArea.removeAll();
        contentArea.removeAll();

        stepIndicatorArea.add(buildSteps());

        if (currentBooking.getStatus() == BookingStatus.CANCELLED) {
            contentArea.add(buildCancelledPanel());
        } else {
            contentArea.add(buildConfirmedPanel());
        }
    }

    private Div buildSteps() {
        Div steps = new Div();
        steps.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("max-width", "620px")
                .set("margin", "0 auto 28px auto")
                .set("gap", "12px");

        boolean cancelled = currentBooking.getStatus() == BookingStatus.CANCELLED;
        steps.add(stepItem("1", "Confirmed", !cancelled, false));
        steps.add(stepItem("2", "Cancelled / Refund Calculated", cancelled, cancelled));
        return steps;
    }

    private Div stepItem(String number, String label, boolean active, boolean completed) {
        Div item = new Div();
        item.getStyle()
                .set("text-align", "center")
                .set("padding", "12px 10px")
                .set("border-radius", "16px")
                .set("background", active ? BLUE : completed ? "#d1fae5" : LIGHT_PANEL)
                .set("color", active ? "white" : completed ? "#047857" : LIGHT_MUTED)
                .set("border", "1px solid " + (active ? BLUE : completed ? GREEN : LIGHT_BORDER))
                .set("box-shadow", active ? "0 12px 30px rgba(0,114,206,0.22)" : "none");

        Span circle = new Span(number);
        circle.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("width", "28px")
                .set("height", "28px")
                .set("border-radius", "999px")
                .set("border", "2px solid currentColor")
                .set("font-weight", "900")
                .set("margin-bottom", "7px");

        Span text = new Span(label);
        text.getStyle()
                .set("display", "block")
                .set("font-size", "15px")
                .set("font-weight", "900");

        item.add(circle, text);
        return item;
    }

    private Div buildConfirmedPanel() {
        Div wrapper = cardPanel();

        H2 heading = sectionHeading("Confirm Cancellation");

        Paragraph note = new Paragraph(
                "This booking is currently confirmed. If you cancel it, the system will apply the coursework rule: 50% cancellation charge and 50% refund, provided the show is not today or in the past."
        );
        note.getStyle()
                .set("color", LIGHT_MUTED)
                .set("font-size", "16px")
                .set("line-height", "1.6")
                .set("margin", "0 0 24px 0");

        Div summary = buildSummaryCard();
        Div refundPreview = buildRefundPreviewCard("Estimated refund");

        Button back = secondaryButton("Back", event ->
                getUI().ifPresent(ui -> ui.navigate("cancellation")));

        Button confirm = primaryButton("Confirm Cancellation", event -> performCancellation());
        confirm.getStyle().set("background", RED);

        wrapper.add(heading, note, summary, refundPreview, actionRow(back, confirm));
        return wrapper;
    }

    private Div buildCancelledPanel() {
        Div wrapper = cardPanel();

        H2 heading = sectionHeading("Cancellation Complete");
        heading.getStyle().set("color", GREEN);

        Paragraph note = new Paragraph(
                "This booking has already been cancelled. The receipt below keeps the booking reference, original amount, cancellation charge and estimated refund for reporting evidence."
        );
        note.getStyle()
                .set("color", LIGHT_MUTED)
                .set("font-size", "16px")
                .set("line-height", "1.6")
                .set("margin", "0 0 24px 0");

        Div summary = buildSummaryCard();
        Div refundReceipt = buildRefundPreviewCard("Refund receipt");

        Button close = primaryButton("Back to Cancellation", event ->
                getUI().ifPresent(ui -> ui.navigate("cancellation")));
        close.getStyle().set("background", GREEN);

        wrapper.add(heading, note, summary, refundReceipt, centerRow(close));
        return wrapper;
    }

    private Div buildSummaryCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", LIGHT_PANEL_SOFT)
                .set("border", "1px solid " + LIGHT_BORDER)
                .set("border-radius", "14px")
                .set("padding", "20px")
                .set("margin-bottom", "22px")
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr 1fr")
                .set("gap", "16px");

        String film = "-";
        String cinema = "-";
        String dateTime = "-";
        String seats = "-";

        if (currentBooking.getScreening() != null) {
            if (currentBooking.getScreening().getFilm() != null) {
                film = currentBooking.getScreening().getFilm().getTitle();
            }

            if (currentBooking.getScreening().getScreen() != null
                    && currentBooking.getScreening().getScreen().getCinema() != null) {
                cinema = currentBooking.getScreening().getScreen().getCinema().getName()
                        + " · Screen "
                        + currentBooking.getScreening().getScreen().getScreenNumber();
            }

            if (currentBooking.getScreening().getScreeningDate() != null
                    && currentBooking.getScreening().getStartTime() != null) {
                dateTime = currentBooking.getScreening().getScreeningDate().format(DATE_FMT)
                        + " "
                        + currentBooking.getScreening().getStartTime().format(TIME_FMT);
            }
        }

        if (currentBooking.getBookingSeats() != null && !currentBooking.getBookingSeats().isEmpty()) {
            seats = currentBooking.getBookingSeats().stream()
                    .map(BookingSeat::getSeat)
                    .filter(seat -> seat != null && seat.getSeatNumber() != null)
                    .map(seat -> seat.getSeatNumber())
                    .sorted()
                    .collect(Collectors.joining(", "));
        }

        card.add(
                info("FILM", film),
                info("CINEMA", cinema),
                info("DATE & TIME", dateTime),
                info("SEATS", seats),
                info("CUSTOMER", safe(currentBooking.getCustomerName())),
                info("STATUS", currentBooking.getStatus().name())
        );

        return card;
    }

    private Div buildRefundPreviewCard(String titleText) {
        Div card = new Div();
        card.getStyle()
                .set("background", "#fffbeb")
                .set("border", "1px solid #f59e0b")
                .set("border-radius", "14px")
                .set("padding", "20px")
                .set("margin-bottom", "22px");

        H3 title = new H3(titleText.toUpperCase());
        title.getStyle()
                .set("font-size", "16px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.06em")
                .set("color", "#92400e")
                .set("margin", "0 0 16px 0");

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr 1fr")
                .set("gap", "16px");

        grid.add(
                info("ORIGINAL AMOUNT", formatMoney(currentBooking.getTotalCost())),
                info("CANCELLATION CHARGE", formatMoney(lastCancellationCharge)),
                info("REFUND AMOUNT", formatMoney(lastRefundAmount))
        );

        Paragraph rule = new Paragraph("Rule applied: 50% cancellation charge. No cancellation is allowed on the day of the show or after the show.");
        rule.getStyle()
                .set("color", "#92400e")
                .set("font-size", "14px")
                .set("font-weight", "700")
                .set("line-height", "1.5")
                .set("margin", "16px 0 0 0");

        card.add(title, grid, rule);
        return card;
    }

    private void performCancellation() {
        try {
            CancellationService.CancellationResult result =
                    cancellationService.cancelBooking(bookingReference);

            currentBooking = result.booking();
            lastCancellationCharge = result.cancellationCharge();
            lastRefundAmount = result.refundAmount();

            Notification.show("Cancellation successful. Refund: " + formatMoney(lastRefundAmount),
                            4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            renderStatusPage();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private Div buildErrorView(String message) {
        Div wrapper = cardPanel();
        wrapper.getStyle()
                .set("margin", "80px auto")
                .set("text-align", "center");

        H2 title = sectionHeading("Unable to load cancellation");
        Paragraph text = new Paragraph(message);
        text.getStyle()
                .set("color", LIGHT_MUTED)
                .set("font-size", "17px");

        Button back = primaryButton("Back to Cancellation", event ->
                getUI().ifPresent(ui -> ui.navigate("cancellation")));

        wrapper.add(title, text, centerRow(back));
        return wrapper;
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
                .set("box-shadow", "0 18px 45px rgba(15, 23, 42, 0.10)")
                .set("box-sizing", "border-box");
        return panel;
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

    private Div info(String label, String value) {
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
                .set("line-height", "1.35")
                .set("overflow-wrap", "anywhere");

        box.add(h, v);
        return box;
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

    private BigDecimal estimateCancellationCharge(Booking booking) {
        if (booking == null || booking.getTotalCost() == null) {
            return BigDecimal.ZERO;
        }
        return booking.getTotalCost()
                .multiply(CANCELLATION_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal estimateRefundAmount(Booking booking) {
        if (booking == null || booking.getTotalCost() == null) {
            return BigDecimal.ZERO;
        }
        return booking.getTotalCost()
                .subtract(estimateCancellationCharge(booking))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return NumberFormat.getCurrencyInstance(Locale.UK).format(BigDecimal.ZERO);
        }
        return NumberFormat.getCurrencyInstance(Locale.UK).format(amount);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
