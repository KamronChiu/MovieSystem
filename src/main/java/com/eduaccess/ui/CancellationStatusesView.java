package com.eduaccess.ui;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.CancellationRecord;
import com.eduaccess.domain.RefundSummary;
import com.eduaccess.exception.CancellationNotAllowedException;
import com.eduaccess.service.CancellationService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.WildcardParameter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Step-by-step cancellation / refund status flow page.
 * <p>
 * Displays 4 status nodes (CONFIRMED → CANCELLED → REFUND_PENDING → REFUNDED)
 * as a visual progress indicator. The user can confirm the current step to
 * advance the booking status, or go back to the cancellation listing page.
 * <p>
 * Key business rule: status only changes when the user explicitly clicks the
 * confirm button for the current step. Returning to the cancellation page
 * without confirming preserves the previous status.
 * <p>
 * Refund calculations are performed by {@link com.eduaccess.service.RefundCalculator}
 * via {@link CancellationService#calculateRefund(String)}; the UI never does
 * arithmetic directly.
 */
@Route(value = "cancellation-statuses", layout = MainLayout.class)
@PageTitle("HCBS — Cancellation Status")
public class CancellationStatusesView extends Div
        implements HasUrlParameter<String>, BeforeEnterObserver {

    private final LoginService loginService;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkCancellationAccess(event, loginService);
    }

    // ── Design tokens (matching BookingView) ──────────────────────────────
    private static final String DARK_BG = "#020b1d";
    private static final String BLUE = "#0072ce";
    private static final String LIGHT_BG = "#f4f7fb";
    private static final String LIGHT_PANEL = "#ffffff";
    private static final String LIGHT_PANEL_SOFT = "#eef4fb";
    private static final String LIGHT_TEXT = "#142033";
    private static final String LIGHT_MUTED = "#64748b";
    private static final String LIGHT_BORDER = "#d8e2ef";
    private static final String GREEN = "#10b981";

    // The 4 statuses shown as steps
    private static final BookingStatus[] FLOW = {
            BookingStatus.CONFIRMED,
            BookingStatus.CANCELLED,
            BookingStatus.REFUND_PENDING,
            BookingStatus.REFUNDED
    };

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.UK);
    private static final DateTimeFormatter STAMP_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.UK);

    private final CancellationService cancellationService;

    private String bookingReference;
    private Booking currentBooking;
    private RefundSummary currentRefundSummary;

    /**
     * Cancellation reason captured from the user's TextArea on steps 1 / 2.
     * Persisted to {@link CancellationRecord} when the DB transitions into
     * {@code CANCELLED} (and on every later step that re-renders the field).
     */
    private String currentReason = "";

    /**
     * UI step pointer (0..3) — independent from DB status.
     * <p>
     * The flow has 4 steps (CONFIRMED, CANCELLED, REFUND_PENDING, REFUNDED).
     * Confirming a step advances this pointer; the DB status is mutated
     * only at certain steps as per business rules:
     * <ul>
     *   <li>Step 0 (Confirmed page) confirm: no DB change, pointer 0→1</li>
     *   <li>Step 1 (Cancelled page) confirm: DB → CANCELLED, pointer 1→2</li>
     *   <li>Step 2 (Refund Pending page) confirm: DB → REFUND_PENDING, pointer 2→3</li>
     *   <li>Step 3 (Refunded page): receipt — auto-finalises DB to REFUNDED</li>
     * </ul>
     */
    private int currentStepIndex = 0;

    private final Div stepIndicatorArea = new Div();
    private final Div stepContentArea = new Div();

    public CancellationStatusesView(CancellationService cancellationService,
                                    LoginService loginService) {
        this.cancellationService = cancellationService;
        this.loginService = loginService;

        setWidthFull();
        getStyle()
                .set("background", LIGHT_BG)
                .set("min-height", "100vh")
                .set("color", LIGHT_TEXT);
    }

    @Override
    public void setParameter(BeforeEvent event, @WildcardParameter String ref) {
        this.bookingReference = ref;
        loadBookingAndRender();
    }

    // ── Data loading ──────────────────────────────────────────────────────

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

        // Calculate refund summary for display
        currentRefundSummary = cancellationService.calculateRefund(bookingReference);

        // TASK 6 — preload any existing cancellation reason so the user can
        // edit it when resuming the flow at a later step.
        currentReason = cancellationService.findCancellationRecord(bookingReference)
                .map(CancellationRecord::getCancellationReason)
                .orElse("");

        // Resume the flow at the step matching the booking's current DB status
        currentStepIndex = initialStepIndexFor(currentBooking.getStatus());

        Div page = new Div();
        page.getStyle()
                .set("max-width", "960px")
                .set("margin", "0 auto")
                .set("padding", "44px 48px 80px 48px")
                .set("box-sizing", "border-box");

        // Header with back link
        page.add(buildHeader());

        // Step indicator + content
        page.add(stepIndicatorArea, stepContentArea);

        add(page);

        renderCurrentStep();
    }

    // ── Header ────────────────────────────────────────────────────────────

    private Div buildHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("margin-bottom", "28px");

        Div left = new Div();

        H2 title = new H2("REFUND PROGRESS");
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

        Button backBtn = secondaryButton("Back to Cancellation", e ->
                getUI().ifPresent(ui -> ui.navigate("cancellation")));
        backBtn.getStyle().set("margin", "0");

        header.add(left, backBtn);
        return header;
    }

    // ── Step indicator ────────────────────────────────────────────────────

    private void renderCurrentStep() {
        stepIndicatorArea.removeAll();
        stepContentArea.removeAll();

        stepIndicatorArea.add(buildSteps());
        stepContentArea.add(buildStepContent());
    }

    private Div buildSteps() {
        Div steps = new Div();
        steps.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(4, 1fr)")
                .set("max-width", "820px")
                .set("margin", "0 auto 28px auto")
                .set("gap", "12px");

        for (int i = 0; i < FLOW.length; i++) {
            boolean completed = i < currentStepIndex;
            boolean active = i == currentStepIndex;
            steps.add(stepItem(String.valueOf(i + 1), FLOW[i].getDisplayName(), completed, active));
        }

        return steps;
    }

    private Div stepItem(String number, String label, boolean completed, boolean active) {
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
        return item;
    }

    // ── Step content ──────────────────────────────────────────────────────

    private Div buildStepContent() {
        // Reached the final "Refunded" step — auto-finalise DB then show receipt.
        if (currentStepIndex >= FLOW.length - 1) {
            autoFinaliseToRefunded();
            return buildCompletedStep();
        }
        return buildActiveStep(currentStepIndex);
    }

    /**
     * When the UI pointer reaches step 4 (Refunded), make sure the DB is
     * actually REFUNDED so the receipt reflects the final state. This is
     * a no-op if the booking is already REFUNDED.
     */
    private void autoFinaliseToRefunded() {
        try {
            if (currentBooking.getStatus() == BookingStatus.REFUND_PENDING) {
                currentBooking = cancellationService.advanceStatus(
                        bookingReference, BookingStatus.REFUNDED);
                currentRefundSummary = cancellationService.calculateRefund(bookingReference);
            }
        } catch (CancellationNotAllowedException ignored) {
            // Receipt is still rendered; DB will remain REFUND_PENDING.
        }
    }

    private Div buildActiveStep(int stepIndex) {
        BookingStatus stepStatus = FLOW[stepIndex];
        Div wrapper = cardPanel();

        H2 heading = sectionHeading(stepHeading(stepStatus));

        Paragraph description = new Paragraph(stepDescription(stepStatus));
        description.getStyle()
                .set("color", LIGHT_MUTED)
                .set("font-size", "16px")
                .set("line-height", "1.6")
                .set("margin", "0 0 24px 0");

        // Booking summary card (always visible — non-financial data only).
        Div summaryCard = buildSummaryCard();

        // TASK 6 — reason TextArea is offered on the early steps where the
        // user is still describing why they're cancelling. Once the booking
        // has reached REFUND_PENDING the cancellation rationale is locked.
        Div reasonSection = new Div();
        if (stepIndex == 0 || stepIndex == 1) {
            reasonSection = buildReasonSection();
        }

        // VIP checkbox + refund breakdown card — ONLY visible on step 3
        // (Refund Pending page, stepIndex == 2). Steps 1 and 2 must not
        // expose any financial information whatsoever.
        Div vipSection = new Div();
        Div refundCard = new Div();
        final Checkbox[] vipCheckboxHolder = new Checkbox[1];

        if (stepIndex == 2) {
            Checkbox vipCheckbox = new Checkbox("Mark this customer as VIP (+20% refund bonus)");
            vipCheckbox.setValue(currentBooking.isVip());
            vipCheckbox.getStyle()
                    .set("margin-top", "16px")
                    .set("margin-bottom", "16px")
                    .set("font-weight", "700")
                    .set("color", LIGHT_TEXT);
            // Live preview: persist the toggle and re-render so the
            // breakdown reflects the new VIP value immediately.
            vipCheckbox.addValueChangeListener(e -> {
                cancellationService.updateVipFlag(bookingReference, e.getValue());
                currentBooking = cancellationService.findBookingByReference(bookingReference)
                        .orElse(currentBooking);
                currentRefundSummary = cancellationService.calculateRefund(bookingReference);
                renderCurrentStep();
            });
            vipSection.add(vipCheckbox);
            vipCheckboxHolder[0] = vipCheckbox;

            refundCard = buildRefundBreakdownCard(stepStatus);
        }

        Button confirmBtn = primaryButton(
                stepConfirmLabel(stepStatus),
                e -> confirmStep(stepIndex, vipCheckboxHolder[0])
        );

        Button backBtn = secondaryButton("Back to Cancellation", e ->
                getUI().ifPresent(ui -> ui.navigate("cancellation")));

        wrapper.add(heading, description, summaryCard, reasonSection, vipSection, refundCard,
                actionRow(backBtn, confirmBtn));
        return wrapper;
    }

    /**
     * Builds the "Reason for cancellation" TextArea shown on steps 1 and 2.
     * The value is bound to {@link #currentReason} and pushed to the
     * persisted {@link CancellationRecord} when the next confirm advances
     * the booking past {@code CONFIRMED}.
     */
    private Div buildReasonSection() {
        Div section = new Div();
        section.getStyle().set("margin-bottom", "22px");

        TextArea reasonField = new TextArea("Reason for cancellation");
        reasonField.setPlaceholder("Optional. e.g. Schedule conflict, change of plans, etc.");
        reasonField.setMaxLength(500);
        reasonField.setWidthFull();
        reasonField.setValueChangeMode(
                com.vaadin.flow.data.value.ValueChangeMode.EAGER);
        reasonField.setValue(currentReason == null ? "" : currentReason);
        reasonField.addValueChangeListener(e ->
                currentReason = e.getValue() == null ? "" : e.getValue());
        reasonField.getStyle()
                .set("--lumo-contrast-10pct", "#eef4fb")
                .set("font-weight", "600");
        section.add(reasonField);
        return section;
    }

    private Div buildCompletedStep() {
        Div wrapper = cardPanel();

        H2 heading = sectionHeading("REFUND COMPLETE — RECEIPT");
        heading.getStyle().set("color", GREEN);

        // Success icon
        Span successIcon = new Span("✓");
        successIcon.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("width", "48px")
                .set("height", "48px")
                .set("border-radius", "999px")
                .set("background", GREEN)
                .set("color", "white")
                .set("font-size", "24px")
                .set("font-weight", "900")
                .set("margin", "0 auto 18px auto");

        Div iconWrap = new Div(successIcon);
        iconWrap.getStyle()
                .set("display", "flex")
                .set("justify-content", "center")
                .set("margin-bottom", "12px");

        Paragraph note = new Paragraph(
                "The refund has been fully processed. No further action is required.");
        note.getStyle()
                .set("color", LIGHT_MUTED)
                .set("font-size", "16px")
                .set("line-height", "1.6")
                .set("margin", "0 0 24px 0")
                .set("text-align", "center");

        // Receipt card
        Div receiptCard = buildReceiptCard();

        Button backBtn = primaryButton("Back to Cancellation", e ->
                getUI().ifPresent(ui -> ui.navigate("cancellation")));
        backBtn.getStyle().set("background", GREEN);

        wrapper.add(iconWrap, heading, note, receiptCard, centerRow(backBtn));
        return wrapper;
    }

    // ── Summary card (booking details) ────────────────────────────────────

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

        String filmTitle = "N/A";
        String cinemaInfo = "N/A";
        String dateInfo = "N/A";
        if (currentBooking.getScreening() != null) {
            if (currentBooking.getScreening().getFilm() != null) {
                filmTitle = currentBooking.getScreening().getFilm().getTitle();
            }
            if (currentBooking.getScreening().getScreen() != null
                    && currentBooking.getScreening().getScreen().getCinema() != null) {
                cinemaInfo = currentBooking.getScreening().getScreen().getCinema().getName();
            }
            if (currentBooking.getScreening().getScreeningDate() != null) {
                dateInfo = currentBooking.getScreening().getScreeningDate().format(DATE_FMT)
                        + " " + currentBooking.getScreening().getStartTime().format(TIME_FMT);
            }
        }

        String seats = "None";
        if (currentBooking.getBookingSeats() != null && !currentBooking.getBookingSeats().isEmpty()) {
            seats = currentBooking.getBookingSeats().stream()
                    .filter(bs -> bs.getSeat() != null)
                    .map(bs -> bs.getSeat().getSeatNumber())
                    .sorted()
                    .collect(Collectors.joining(", "));
        }

        card.add(
                dialogInfo("FILM", filmTitle),
                dialogInfo("CINEMA", cinemaInfo),
                dialogInfo("DATE & TIME", dateInfo),
                dialogInfo("SEATS", seats),
                dialogInfo("CUSTOMER", currentBooking.getCustomerName()),
                dialogInfoWithBadge("STATUS", currentBooking.getStatus())
        );

        return card;
    }

    // ── Refund breakdown card ─────────────────────────────────────────────

    private Div buildRefundBreakdownCard(BookingStatus currentStatus) {
        Div card = new Div();
        card.getStyle()
                .set("background", "#fffbeb")
                .set("border", "1px solid #f59e0b")
                .set("border-radius", "14px")
                .set("padding", "20px")
                .set("margin-bottom", "22px");

        H3 label = new H3("REFUND BREAKDOWN");
        label.getStyle()
                .set("font-size", "16px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.06em")
                .set("color", "#92400e")
                .set("margin", "0 0 16px 0");

        card.add(label);

        if (currentRefundSummary != null) {
            // Adjustment descriptions
            Div adjustmentsList = new Div();
            adjustmentsList.getStyle()
                    .set("margin-bottom", "16px")
                    .set("display", "flex")
                    .set("flex-direction", "column")
                    .set("gap", "6px");

            for (String adj : currentRefundSummary.getAdjustments()) {
                Span adjSpan = new Span("• " + adj);
                adjSpan.getStyle()
                        .set("font-size", "14px")
                        .set("font-weight", "700")
                        .set("color", "#78350f");
                adjustmentsList.add(adjSpan);
            }
            card.add(adjustmentsList);

            // Amounts grid
            Div amounts = new Div();
            amounts.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "1fr 1fr 1fr")
                    .set("gap", "16px");

            amounts.add(
                    dialogInfo("ORIGINAL AMOUNT", formatMoney(currentRefundSummary.getOriginalAmount())),
                    dialogInfo("REFUND AMOUNT", formatMoney(currentRefundSummary.getRefundAmount())),
                    dialogInfo("CANCELLATION FEE", formatMoney(currentRefundSummary.getFeeAmount()))
            );
            card.add(amounts);
        } else {
            Span placeholder = new Span("Refund details will be displayed after calculation.");
            placeholder.getStyle().set("color", "#92400e").set("font-style", "italic");
            card.add(placeholder);
        }

        return card;
    }

    // ── Receipt card (terminal state) ─────────────────────────────────────

    private Div buildReceiptCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", "#ecfdf5")
                .set("border", "2px solid " + GREEN)
                .set("border-radius", "14px")
                .set("padding", "24px")
                .set("margin-bottom", "22px");

        H3 receiptLabel = new H3("REFUND RECEIPT");
        receiptLabel.getStyle()
                .set("font-size", "16px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.06em")
                .set("color", "#065f46")
                .set("margin", "0 0 18px 0");

        card.add(receiptLabel);

        // Booking reference and basic info
        Div topRow = new Div();
        topRow.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "16px")
                .set("margin-bottom", "16px");

        String filmTitle = "N/A";
        String cinemaInfo = "N/A";
        String dateInfo = "N/A";
        String seats = "None";
        if (currentBooking.getScreening() != null) {
            if (currentBooking.getScreening().getFilm() != null) {
                filmTitle = currentBooking.getScreening().getFilm().getTitle();
            }
            if (currentBooking.getScreening().getScreen() != null
                    && currentBooking.getScreening().getScreen().getCinema() != null) {
                cinemaInfo = currentBooking.getScreening().getScreen().getCinema().getName();
            }
            if (currentBooking.getScreening().getScreeningDate() != null) {
                dateInfo = currentBooking.getScreening().getScreeningDate().format(DATE_FMT)
                        + " " + currentBooking.getScreening().getStartTime().format(TIME_FMT);
            }
        }
        if (currentBooking.getBookingSeats() != null && !currentBooking.getBookingSeats().isEmpty()) {
            seats = currentBooking.getBookingSeats().stream()
                    .filter(bs -> bs.getSeat() != null)
                    .map(bs -> bs.getSeat().getSeatNumber())
                    .sorted()
                    .collect(Collectors.joining(", "));
        }

        topRow.add(
                receiptInfo("BOOKING REF", currentBooking.getBookingReference()),
                receiptInfo("CUSTOMER", currentBooking.getCustomerName()),
                receiptInfo("FILM", filmTitle),
                receiptInfo("CINEMA", cinemaInfo),
                receiptInfo("DATE & TIME", dateInfo),
                receiptInfo("SEATS", seats)
        );
        card.add(topRow);

        // Divider
        Div divider = new Div();
        divider.getStyle()
                .set("border-top", "1px solid #a7f3d0")
                .set("margin", "16px 0");
        card.add(divider);

        // Refund amounts
        if (currentRefundSummary != null) {
            Div refundRow = new Div();
            refundRow.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "1fr 1fr 1fr")
                    .set("gap", "16px")
                    .set("margin-bottom", "16px");

            refundRow.add(
                    receiptInfo("ORIGINAL AMOUNT", formatMoney(currentRefundSummary.getOriginalAmount())),
                    receiptInfo("REFUND AMOUNT", formatMoney(currentRefundSummary.getRefundAmount())),
                    receiptInfo("CANCELLATION FEE", formatMoney(currentRefundSummary.getFeeAmount()))
            );
            card.add(refundRow);

            // Adjustment details
            if (!currentRefundSummary.getAdjustments().isEmpty()) {
                Div adjSection = new Div();
                adjSection.getStyle().set("margin-bottom", "12px");

                Span adjLabel = new Span("ADJUSTMENTS APPLIED:");
                adjLabel.getStyle()
                        .set("font-size", "12px")
                        .set("font-weight", "900")
                        .set("letter-spacing", "0.06em")
                        .set("color", "#065f46")
                        .set("display", "block")
                        .set("margin-bottom", "8px");

                adjSection.add(adjLabel);

                for (String adj : currentRefundSummary.getAdjustments()) {
                    Span adjSpan = new Span("• " + adj);
                    adjSpan.getStyle()
                            .set("font-size", "13px")
                            .set("font-weight", "700")
                            .set("color", "#047857")
                            .set("display", "block")
                            .set("margin-bottom", "4px");
                    adjSection.add(adjSpan);
                }
                card.add(adjSection);
            }

            // VIP indicator
            if (currentBooking.isVip()) {
                Span vipBadge = new Span("VIP CUSTOMER");
                vipBadge.getStyle()
                        .set("display", "inline-block")
                        .set("padding", "4px 12px")
                        .set("border-radius", "999px")
                        .set("background", "#7c3aed")
                        .set("color", "white")
                        .set("font-size", "12px")
                        .set("font-weight", "900")
                        .set("margin-top", "8px");
                card.add(vipBadge);
            }
        }

        // TASK 6 — pull cancellation reason + timestamp from the persisted
        // record so the receipt reflects the audit trail rather than "now".
        CancellationRecord record = cancellationService
                .findCancellationRecord(bookingReference)
                .orElse(null);
        String reasonText = record != null && record.getCancellationReason() != null
                && !record.getCancellationReason().isBlank()
                ? record.getCancellationReason()
                : "Not provided";
        LocalDateTime cancelledAt = record != null && record.getCancelledAt() != null
                ? record.getCancelledAt()
                : LocalDateTime.now();

        // Reason block
        Div reasonBlock = new Div();
        reasonBlock.getStyle()
                .set("margin-bottom", "12px");

        Span reasonLabel = new Span("CANCELLATION REASON");
        reasonLabel.getStyle()
                .set("display", "block")
                .set("color", "#065f46")
                .set("font-size", "12px")
                .set("letter-spacing", "0.08em")
                .set("font-weight", "900")
                .set("margin-bottom", "6px");

        Span reasonValue = new Span(reasonText);
        reasonValue.getStyle()
                .set("display", "block")
                .set("font-size", "15px")
                .set("font-weight", "700")
                .set("color", "#047857")
                .set("line-height", "1.45")
                .set("white-space", "pre-wrap");

        reasonBlock.add(reasonLabel, reasonValue);
        card.add(reasonBlock);

        // Divider
        Div divider2 = new Div();
        divider2.getStyle()
                .set("border-top", "1px solid #a7f3d0")
                .set("margin", "16px 0");
        card.add(divider2);

        // Timestamps row — cancelled at + processed (today)
        Div stampsRow = new Div();
        stampsRow.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "16px");

        stampsRow.add(
                receiptInfo("CANCELLED AT", cancelledAt.format(STAMP_FMT)),
                receiptInfo("PROCESSED ON", LocalDate.now().format(DATE_FMT))
        );
        card.add(stampsRow);

        // Refunded indicator
        if (record != null && record.isRefunded()) {
            Span refundedBadge = new Span("REFUND ISSUED");
            refundedBadge.getStyle()
                    .set("display", "inline-block")
                    .set("margin-top", "12px")
                    .set("padding", "4px 12px")
                    .set("border-radius", "999px")
                    .set("background", GREEN)
                    .set("color", "white")
                    .set("font-size", "12px")
                    .set("font-weight", "900");
            card.add(refundedBadge);
        }

        return card;
    }

    // ── Step-specific text ────────────────────────────────────────────────

    private String stepHeading(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> "Confirm Cancellation";
            case CANCELLED -> "Request Refund";
            case REFUND_PENDING -> "Process Refund";
            case REFUNDED -> "Refund Complete";
        };
    }

    private String stepDescription(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> "This booking is currently confirmed. Click the button below to proceed "
                    + "to the next step. Your booking status will not change until you confirm the next step.";
            case CANCELLED -> "Confirm the cancellation to lock it in. Once confirmed, the booking status "
                    + "will be updated to Cancelled and the refund process can begin.";
            case REFUND_PENDING -> "Review the refund breakdown below. Mark the customer as VIP if applicable "
                    + "and click the button below to submit the refund request.";
            case REFUNDED -> "The refund has been fully processed.";
        };
    }

    private String stepConfirmLabel(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> "Proceed";
            case CANCELLED -> "Confirm Cancellation";
            case REFUND_PENDING -> "Submit Refund Request";
            case REFUNDED -> "Close";
        };
    }

    // ── Business action ───────────────────────────────────────────────────

    /**
     * Maps a DB status back to its UI step pointer so the user resumes the
     * flow at the right step when re-entering from the cancellation listing.
     */
    private int initialStepIndexFor(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> 0;
            case CANCELLED -> 1;
            case REFUND_PENDING -> 2;
            case REFUNDED -> 3;
        };
    }

    /**
     * Handles a confirm-click on the given UI step. The DB transition (if any)
     * is determined by {@code stepIndex} — not by the booking's current
     * status — so that step 1 (Confirmed page) does not mutate the DB at all.
     *
     * @param stepIndex   the UI step the user just confirmed
     * @param vipCheckbox the VIP checkbox shown on step 3, or {@code null}
     */
    private void confirmStep(int stepIndex, Checkbox vipCheckbox) {
        try {
            // Persist VIP flag if the checkbox was rendered on this step.
            if (vipCheckbox != null) {
                cancellationService.updateVipFlag(bookingReference, vipCheckbox.getValue());
            }

            switch (stepIndex) {
                case 0 -> { /* Step 1 (Confirmed) — no DB change by design. */ }
                case 1 -> advanceIfNeeded(BookingStatus.CANCELLED);
                case 2 -> advanceIfNeeded(BookingStatus.REFUND_PENDING);
                default -> { /* Step 4 (Refunded) handled by autoFinaliseToRefunded */ }
            }

            // TASK 6 — push the latest reason text into the persisted record.
            // No-op if no record exists yet (e.g. step 0 before CANCELLED
            // transition); otherwise the current TextArea value is saved.
            cancellationService.updateCancellationReason(bookingReference, currentReason);

            // Reload booking + refund summary after potential DB change.
            currentBooking = cancellationService.findBookingByReference(bookingReference)
                    .orElse(currentBooking);
            currentRefundSummary = cancellationService.calculateRefund(bookingReference);

            // Advance the UI pointer.
            currentStepIndex = Math.min(currentStepIndex + 1, FLOW.length - 1);

            Notification.show("Step " + (currentStepIndex + 1) + " of " + FLOW.length,
                            2500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            renderCurrentStep();

        } catch (CancellationNotAllowedException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Advances the booking to {@code target} only if the current DB status
     * has a valid transition to it. This makes the confirm logic idempotent
     * when the user re-enters the flow at an already-advanced step (e.g.
     * resuming a CANCELLED booking and clicking confirm on step 2 should
     * not throw because the booking is already CANCELLED).
     */
    private void advanceIfNeeded(BookingStatus target) {
        BookingStatus current = currentBooking.getStatus();
        if (current == target) {
            return;
        }
        if (current.canTransitionTo(target)) {
            currentBooking = cancellationService.advanceStatus(bookingReference, target);
        }
        // If neither equal nor a valid transition, silently skip — the DB
        // is already past this step (e.g. user resumed at step 3 with DB
        // already at REFUND_PENDING; clicking confirm just advances UI).
    }

    // ── Error view ────────────────────────────────────────────────────────

    private Div buildErrorView(String message) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("max-width", "600px")
                .set("margin", "80px auto")
                .set("text-align", "center");

        Span icon = new Span("⚠");
        icon.getStyle().set("font-size", "48px");

        Paragraph text = new Paragraph(message);
        text.getStyle().set("font-size", "18px").set("color", LIGHT_MUTED);

        Button back = secondaryButton("Back to Cancellation", e ->
                getUI().ifPresent(ui -> ui.navigate("cancellation")));

        wrapper.add(icon, text, centerRow(back));
        return wrapper;
    }

    // ── Reusable UI helpers (matching BookingView style) ──────────────────

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

    private Button primaryButton(String text,
                                 ComponentEventListener<ClickEvent<Button>> listener) {
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

    private Button secondaryButton(String text,
                                   ComponentEventListener<ClickEvent<Button>> listener) {
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

    private Div dialogInfoWithBadge(String label, BookingStatus status) {
        Div box = new Div();

        Span h = new Span(label);
        h.getStyle()
                .set("display", "block")
                .set("color", LIGHT_MUTED)
                .set("font-size", "13px")
                .set("letter-spacing", "0.08em")
                .set("font-weight", "900")
                .set("margin-bottom", "8px");

        Span badge = new Span(status.getDisplayName());
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "5px 14px")
                .set("border-radius", "999px")
                .set("background", status.getBadgeBackground())
                .set("color", status.getBadgeTextColor())
                .set("font-size", "13px")
                .set("font-weight", "800");

        box.add(h, badge);
        return box;
    }

    private Div receiptInfo(String label, String value) {
        Div box = new Div();

        Span h = new Span(label);
        h.getStyle()
                .set("display", "block")
                .set("color", "#065f46")
                .set("font-size", "12px")
                .set("letter-spacing", "0.08em")
                .set("font-weight", "900")
                .set("margin-bottom", "6px");

        Span v = new Span(value == null || value.isBlank() ? "-" : value);
        v.getStyle()
                .set("display", "block")
                .set("font-size", "15px")
                .set("font-weight", "850")
                .set("color", "#047857")
                .set("line-height", "1.35");

        box.add(h, v);
        return box;
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.UK).format(amount);
    }
}
