package com.eduaccess.ui;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.CancellationRecord;
import com.eduaccess.domain.RefundSummary;
import com.eduaccess.exception.CancellationNotAllowedException;
import com.eduaccess.service.CancellationService;
import com.eduaccess.service.LoginService;
import com.eduaccess.service.compensation.CompensationItem;
import com.eduaccess.service.compensation.CompensationPackage;
import com.eduaccess.service.compensation.VIPBenefitService;
import com.eduaccess.service.email.CancellationEmail;
import com.eduaccess.service.email.CancellationReceipt;
import com.eduaccess.service.email.EmailReceiptService;
import com.eduaccess.service.policy.PolicyRefundResult;
import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundContext;
import com.eduaccess.service.policy.RefundScope;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
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
    private final VIPBenefitService vipBenefitService;
    private final EmailReceiptService emailReceiptService;

    private String bookingReference;
    private Booking currentBooking;
    private RefundSummary currentRefundSummary;

    /**
     * Cancellation reason captured from the user's TextArea on steps 1 / 2.
     * Persisted to {@link CancellationRecord} when the DB transitions into
     * {@code CANCELLED} (and on every later step that re-renders the field).
     */
    private String currentReason = "";

    // TASK 9 — Refund Decision Panel state (step 3 only).
    /** Refund scope selected by the administrator on the decision panel. */
    private RefundScope currentScope = RefundScope.PARTIAL;
    /** Policy chosen on the decision panel. Defaults to Standard. */
    private PolicyType currentPolicyType = PolicyType.STANDARD;
    /** Whether to refund attached food orders. Auto-disabled when no food exists. */
    private boolean currentIncludeFood = true;
    /** Whether to refund the virtual VIP package add-on. Only meaningful for VIP customers. */
    private boolean currentIncludeVipPackage = false;
    /** Cached refundable food total (PENDING/PREPARING orders) so the breakdown can show a hint. */
    private BigDecimal currentFoodAmount = BigDecimal.ZERO;

    // TASK 10 — Compensation engine state (VIP + Emergency only).
    /** Whether to issue the half-price voucher (Emergency + VIP only). */
    private boolean currentIncludeHalfPriceVoucher = true;
    /** Whether to issue the free drink coupon (Emergency + VIP only). */
    private boolean currentIncludeFreeDrinkCoupon = false;

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
                                    LoginService loginService,
                                    VIPBenefitService vipBenefitService,
                                    EmailReceiptService emailReceiptService) {
        this.cancellationService = cancellationService;
        this.loginService = loginService;
        this.vipBenefitService = vipBenefitService;
        this.emailReceiptService = emailReceiptService;

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

        // TASK 9 — preload refundable food total so the decision panel
        // can disable the food line item when nothing can be refunded.
        currentFoodAmount = cancellationService
                .calculateRefundableFoodAmount(currentBooking.getId());
        currentIncludeFood = currentFoodAmount.signum() > 0;
        currentIncludeVipPackage = currentBooking.isVip();

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
        // TASK 9 — step 3 (Refund Pending) renders the Refund Decision Panel
        // instead of the generic active-step layout.
        if (currentStepIndex == 2) {
            return buildRefundDecisionStep();
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
        } catch (IllegalStateException ex) {
            // Booking.transitionTo throws IllegalStateException when the underlying
            // status machine rejects the move. Surface it instead of silently
            // letting Vaadin swallow the exception (which is what made the final
            // step appear to do nothing).
            Notification.show("Auto-finalise rejected: " + ex.getMessage(),
                            6000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (RuntimeException ex) {
            // Any persistence-level failure (e.g. JDBC CHECK constraint violation
            // when an old booking_status enum CHECK still lingers in H2) must
            // not silently break the receipt render.
            Notification.show("Refund finalise failed: " + ex.getMessage(),
                            7000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
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

        // ── TASK 12 — Email Preview / Receipt Preview ─────────────────
        Button emailBtn = secondaryButton("📧  Email Preview",
                e -> openSingleEmailPreviewDialog());
        emailBtn.getStyle()
                .set("border", "1px solid " + BLUE)
                .set("color", BLUE);
        Button receiptBtn = secondaryButton("🧾  Receipt Preview",
                e -> openSingleReceiptPreviewDialog());
        receiptBtn.getStyle()
                .set("border", "1px solid " + GREEN)
                .set("color", "#065f46");
        Div previewRow = new Div(emailBtn, receiptBtn);
        previewRow.getStyle()
                .set("display", "flex")
                .set("justify-content", "center")
                .set("gap", "14px")
                .set("margin-top", "6px")
                .set("margin-bottom", "4px");

        wrapper.add(iconWrap, heading, note, receiptCard, previewRow, centerRow(backBtn));
        return wrapper;
    }

    /**
     * TASK 12 — opens the Email Preview Dialog for the just-completed
     * single-cancellation flow. The VO is rebuilt on demand from the
     * cached refund decision (policy/scope/items) so the preview always
     * reflects what the user actually submitted on step 3.
     */
    private void openSingleEmailPreviewDialog() {
        try {
            RefundContext ctx = cancellationService.buildPolicyContext(
                    bookingReference,
                    currentPolicyType,
                    currentScope,
                    true,
                    currentIncludeFood,
                    currentIncludeVipPackage);
            PolicyRefundResult result = cancellationService
                    .quotePolicyRefund(currentPolicyType, ctx);
            CompensationPackage pkg = vipBenefitService.build(
                    currentPolicyType, ctx,
                    currentIncludeHalfPriceVoucher,
                    currentIncludeFreeDrinkCoupon);
            LocalDateTime stamp = cancellationService.findCancellationRecord(bookingReference)
                    .map(CancellationRecord::getCancelledAt)
                    .orElse(LocalDateTime.now());
            CancellationEmail email = emailReceiptService.buildSingleEmail(
                    currentBooking, currentPolicyType, result, pkg, stamp);
            new EmailPreviewDialog(email).open();
        } catch (RuntimeException ex) {
            Notification.show("Failed to build email preview: " + ex.getMessage(),
                            5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * TASK 12 — opens the Receipt Preview Dialog for the just-completed
     * single-cancellation flow. The receipt is rebuilt the same way as
     * the email preview and includes the policy breakdown, compensation
     * items and the operator who pressed the action.
     */
    private void openSingleReceiptPreviewDialog() {
        try {
            RefundContext ctx = cancellationService.buildPolicyContext(
                    bookingReference,
                    currentPolicyType,
                    currentScope,
                    true,
                    currentIncludeFood,
                    currentIncludeVipPackage);
            PolicyRefundResult result = cancellationService
                    .quotePolicyRefund(currentPolicyType, ctx);
            CompensationPackage pkg = vipBenefitService.build(
                    currentPolicyType, ctx,
                    currentIncludeHalfPriceVoucher,
                    currentIncludeFreeDrinkCoupon);
            String operator = "system";
            try {
                if (loginService.getCurrentUser() != null) {
                    operator = loginService.getCurrentUser().getUsername();
                }
            } catch (RuntimeException ignored) { /* fall through */ }
            LocalDateTime stamp = cancellationService.findCancellationRecord(bookingReference)
                    .map(CancellationRecord::getCancelledAt)
                    .orElse(LocalDateTime.now());
            CancellationReceipt receipt = emailReceiptService.buildSingleReceipt(
                    currentBooking, currentPolicyType, currentScope,
                    result, pkg, operator, stamp);
            new ReceiptPreviewDialog(receipt).open();
        } catch (RuntimeException ex) {
            Notification.show("Failed to build receipt preview: " + ex.getMessage(),
                            5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
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

    // ── TASK 9 — Refund Decision Panel (step 3) ───────────────────────────

    /**
     * Builds the step-3 Refund Decision Panel.
     * <p>
     * The panel does <strong>not</strong> repeat the booking summary card
     * (already confirmed on step 2 and displayed in detail on step 4).
     * It exposes four controls:
     * <ol>
     *   <li>Refund Type — {@code RadioButtonGroup<RefundScope>}.</li>
     *   <li>Refund Items — three checkboxes (Movie always selected and
     *       disabled per the brief, Food disabled when no refundable food
     *       exists, VIP Package disabled for non-VIP customers).</li>
     *   <li>Policy Selection — {@code RadioButtonGroup<PolicyType>}.</li>
     *   <li>Refund Breakdown — re-rendered live whenever any control changes.</li>
     * </ol>
     * Business coupling rule: choosing {@link RefundScope#FULL} forces the
     * policy to {@link PolicyType#EMERGENCY} because only the Emergency
     * Policy permits a same-day total refund.
     */
    private Div buildRefundDecisionStep() {
        Div wrapper = cardPanel();

        H2 heading = sectionHeading("PROCESS REFUND");

        Paragraph description = new Paragraph(
                "Choose the refund scope, line items and policy. The breakdown "
                        + "below recalculates instantly so you can compare results "
                        + "before submitting the request.");
        description.getStyle()
                .set("color", LIGHT_MUTED)
                .set("font-size", "16px")
                .set("line-height", "1.6")
                .set("margin", "0 0 24px 0");

        // ── Refund Type ───────────────────────────────────────────────
        RadioButtonGroup<RefundScope> scopeGroup = new RadioButtonGroup<>();
        scopeGroup.setItems(RefundScope.FULL, RefundScope.PARTIAL);
        scopeGroup.setItemLabelGenerator(s -> s == RefundScope.FULL
                ? "Full Refund  (Special Case)"
                : "Partial Refund  (Standard)");
        scopeGroup.setValue(currentScope);
        scopeGroup.getStyle().set("display", "flex").set("gap", "24px");

        Div scopeSection = decisionSection("REFUND TYPE", scopeGroup);

        // ── Refund Items ──────────────────────────────────────────────
        Checkbox movieCb = new Checkbox("Movie Tickets — " + formatMoney(currentBooking.getTotalCost()));
        movieCb.setValue(true);
        movieCb.setEnabled(false); // brief: movie tickets are always refunded on step 3
        styleCheckbox(movieCb);

        Checkbox foodCb = new Checkbox("Food Combo / Drinks — " + formatMoney(currentFoodAmount));
        boolean hasFood = currentFoodAmount.signum() > 0;
        foodCb.setValue(hasFood && currentIncludeFood);
        foodCb.setEnabled(hasFood);
        styleCheckbox(foodCb);

        boolean isVip = currentBooking.isVip();
        BigDecimal vipPkgAmount = isVip
                ? CancellationService.VIP_PACKAGE_FEE
                : BigDecimal.ZERO;
        Checkbox vipPkgCb = new Checkbox("VIP Package — " + formatMoney(vipPkgAmount));
        vipPkgCb.setValue(isVip && currentIncludeVipPackage);
        vipPkgCb.setEnabled(isVip);
        styleCheckbox(vipPkgCb);

        Div itemsBox = new Div(movieCb, foodCb, vipPkgCb);
        itemsBox.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "10px");
        Div itemsSection = decisionSection("REFUND ITEMS", itemsBox);

        // ── Policy Selection ──────────────────────────────────────────
        RadioButtonGroup<PolicyType> policyGroup = new RadioButtonGroup<>();
        policyGroup.setItems(PolicyType.STANDARD, PolicyType.VIP, PolicyType.EMERGENCY);
        policyGroup.setItemLabelGenerator(PolicyType::getDisplayName);
        policyGroup.setValue(currentPolicyType);
        policyGroup.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "6px");
        Div policySection = decisionSection("POLICY SELECTION", policyGroup);

        // ── Breakdown holder (re-rendered on every change) ────────────
        Div breakdownHolder = new Div();
        breakdownHolder.getStyle().set("margin-top", "4px");

        // ── TASK 10 — Compensation Card holder (refreshed alongside breakdown) ──
        Div compensationHolder = new Div();
        compensationHolder.getStyle().set("margin-top", "18px");

        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                breakdownHolder.removeAll();
                RefundContext ctx = cancellationService.buildPolicyContext(
                        bookingReference,
                        currentPolicyType,
                        currentScope,
                        true,                       // movie is always included on step 3
                        currentIncludeFood,
                        currentIncludeVipPackage);
                PolicyRefundResult result = cancellationService
                        .quotePolicyRefund(currentPolicyType, ctx);
                if (result != null) {
                    breakdownHolder.add(buildPolicyBreakdownCard(result));
                }

                // TASK 10 — rebuild compensation card based on the same context.
                compensationHolder.removeAll();
                compensationHolder.add(
                        CancellationStatusesView.this.buildCompensationCard(ctx, this));
            }
        };

        // Wire listeners — every change re-runs the policy strategy via the factory.
        scopeGroup.addValueChangeListener(e -> {
            currentScope = e.getValue() == null ? RefundScope.PARTIAL : e.getValue();
            if (currentScope == RefundScope.FULL && currentPolicyType != PolicyType.EMERGENCY) {
                // Business rule: full refund must be paired with Emergency Policy.
                currentPolicyType = PolicyType.EMERGENCY;
                policyGroup.setValue(PolicyType.EMERGENCY);
            }
            refresh.run();
        });
        foodCb.addValueChangeListener(e -> {
            currentIncludeFood = Boolean.TRUE.equals(e.getValue());
            refresh.run();
        });
        vipPkgCb.addValueChangeListener(e -> {
            currentIncludeVipPackage = Boolean.TRUE.equals(e.getValue());
            refresh.run();
        });
        policyGroup.addValueChangeListener(e -> {
            currentPolicyType = e.getValue() == null ? PolicyType.STANDARD : e.getValue();
            refresh.run();
        });

        // Initial breakdown render.
        refresh.run();

        // ── Action row ────────────────────────────────────────────────
        Button submitBtn = primaryButton("Submit Refund Request",
                e -> submitPolicyRefundAction());
        Button backBtn = secondaryButton("Back to Cancellation",
                e -> getUI().ifPresent(ui -> ui.navigate("cancellation")));

        wrapper.add(
                heading,
                description,
                scopeSection,
                itemsSection,
                policySection,
                breakdownHolder,
                compensationHolder,
                actionRow(backBtn, submitBtn)
        );
        return wrapper;
    }

    /**
     * Renders the live Refund Breakdown card driven by the result of the
     * currently selected {@link PolicyType}. Includes the per-item rule
     * lines emitted by the strategy plus the Final Refund total and an
     * optional voucher line (Emergency + VIP).
     */
    private Div buildPolicyBreakdownCard(PolicyRefundResult result) {
        Div card = new Div();
        card.getStyle()
                .set("background", "#fffbeb")
                .set("border", "1px solid #f59e0b")
                .set("border-radius", "14px")
                .set("padding", "22px")
                .set("margin-top", "6px");

        H3 label = new H3("REFUND BREAKDOWN — " + result.getPolicyType().getDisplayName());
        label.getStyle()
                .set("font-size", "16px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.06em")
                .set("color", "#92400e")
                .set("margin", "0 0 14px 0");
        card.add(label);

        // Rule lines emitted by the strategy.
        Div lines = new Div();
        lines.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "5px")
                .set("margin-bottom", "16px");
        for (String line : result.getBreakdownLines()) {
            Span lineSpan = new Span(line);
            lineSpan.getStyle()
                    .set("font-size", "14px")
                    .set("font-weight", "700")
                    .set("color", "#78350f");
            lines.add(lineSpan);
        }
        card.add(lines);

        // Per-line refund amounts grid.
        Div amounts = new Div();
        amounts.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr 1fr")
                .set("gap", "16px")
                .set("margin-bottom", "14px");
        amounts.add(
                dialogInfo("MOVIE REFUND", formatMoney(result.getMovieRefund())),
                dialogInfo("FOOD REFUND", formatMoney(result.getFoodRefund())),
                dialogInfo("VIP PACKAGE", formatMoney(result.getVipPackageRefund()))
        );
        card.add(amounts);

        // Optional voucher line — only present for Emergency + VIP.
        if (result.getVoucher().signum() > 0) {
            Span voucherLine = new Span(
                    "VIP COMPENSATION: 50%-off voucher (face value "
                            + formatMoney(result.getVoucher()) + ")");
            voucherLine.getStyle()
                    .set("display", "block")
                    .set("padding", "8px 12px")
                    .set("border-radius", "10px")
                    .set("background", "#fde68a")
                    .set("color", "#7c2d12")
                    .set("font-size", "14px")
                    .set("font-weight", "800")
                    .set("margin-bottom", "14px");
            card.add(voucherLine);
        }

        // Final refund footer — highlighted total.
        Div finalRow = new Div();
        finalRow.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("padding", "14px 18px")
                .set("border-radius", "12px")
                .set("background", BLUE)
                .set("color", "white");
        Span finalLabel = new Span("FINAL REFUND");
        finalLabel.getStyle()
                .set("font-size", "14px")
                .set("letter-spacing", "0.08em")
                .set("font-weight", "900");
        Span finalValue = new Span(formatMoney(result.getFinalRefund()));
        finalValue.getStyle()
                .set("font-size", "24px")
                .set("font-weight", "900");
        finalRow.add(finalLabel, finalValue);
        card.add(finalRow);

        return card;
    }

    // ── TASK 10 — Compensation Card ──────────────────────────────────

    /**
     * Builds the Compensation Card block on the Refund Decision Panel.
     * <p>
     * Layout depends on the customer / policy combination:
     * <ul>
     *   <li><b>non-VIP</b> — minimal informational card explaining no
     *       VIP benefits apply (kept on screen so the absence of compensation
     *       is explicitly demonstrated rather than silently hidden).</li>
     *   <li><b>VIP + Emergency</b> — two opt-in checkboxes
     *       (Half-price Voucher / Free Drink Coupon) plus a Details button
     *       that opens a {@link Dialog} listing every issued item.</li>
     *   <li><b>VIP + Standard / VIP Policy</b> — read-only Bonus Refund
     *       label demonstrating the +20% benefit already baked into
     *       {@code VIPPolicy} (70% vs Standard 50%).</li>
     * </ul>
     *
     * @param ctx     refund context built by
     *                {@link CancellationService#buildPolicyContext}
     * @param refresh callback used by the Emergency-only checkboxes to
     *                regenerate the breakdown + compensation card live
     * @return the rendered Compensation Card
     */
    private Div buildCompensationCard(RefundContext ctx, Runnable refresh) {
        Div card = new Div();
        boolean isVip = currentBooking != null && currentBooking.isVip();

        // Card chrome — purple accent so it stands out from the breakdown.
        card.getStyle()
                .set("background", isVip ? "#f5f3ff" : LIGHT_PANEL_SOFT)
                .set("border", "1px solid " + (isVip ? "#a855f7" : LIGHT_BORDER))
                .set("border-radius", "14px")
                .set("padding", "22px");

        H3 label = new H3(isVip ? "COMPENSATION CARD — VIP BENEFITS"
                : "COMPENSATION CARD");
        label.getStyle()
                .set("font-size", "16px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.06em")
                .set("color", isVip ? "#6b21a8" : LIGHT_MUTED)
                .set("margin", "0 0 14px 0");
        card.add(label);

        if (!isVip) {
            Span msg = new Span(
                    "Standard customer — no VIP compensation applicable. "
                            + "Upgrade the customer to VIP on step 3 to unlock benefits.");
            msg.getStyle()
                    .set("display", "block")
                    .set("color", LIGHT_MUTED)
                    .set("font-size", "14px")
                    .set("font-weight", "700")
                    .set("font-style", "italic");
            card.add(msg);
            return card;
        }

        // VIP — branch on policy.
        if (currentPolicyType == PolicyType.EMERGENCY) {
            // Emergency: two opt-in checkboxes.
            Span hint = new Span(
                    "Emergency Policy unlocks two optional VIP vouchers. "
                            + "Tick the boxes to include them in the refund package.");
            hint.getStyle()
                    .set("display", "block")
                    .set("color", "#6b21a8")
                    .set("font-size", "13px")
                    .set("font-weight", "700")
                    .set("margin-bottom", "12px");
            card.add(hint);

            Checkbox halfPriceCb = new Checkbox(
                    "Half-price Voucher (face value = 50% of ticket)");
            halfPriceCb.setValue(currentIncludeHalfPriceVoucher);
            styleCheckbox(halfPriceCb);
            halfPriceCb.addValueChangeListener(e -> {
                currentIncludeHalfPriceVoucher = Boolean.TRUE.equals(e.getValue());
                refresh.run();
            });

            Checkbox freeDrinkCb = new Checkbox("Free Drink Coupon");
            freeDrinkCb.setValue(currentIncludeFreeDrinkCoupon);
            styleCheckbox(freeDrinkCb);
            freeDrinkCb.addValueChangeListener(e -> {
                currentIncludeFreeDrinkCoupon = Boolean.TRUE.equals(e.getValue());
                refresh.run();
            });

            Div boxes = new Div(halfPriceCb, freeDrinkCb);
            boxes.getStyle()
                    .set("display", "flex")
                    .set("flex-direction", "column")
                    .set("gap", "8px")
                    .set("margin-bottom", "16px");
            card.add(boxes);
        } else {
            // Standard / VIP Policy: read-only Bonus Refund label.
            Span bonusBadge = new Span(currentPolicyType == PolicyType.VIP
                    ? "✓ BONUS REFUND — +20% extra ticket refund (already applied)"
                    : "BONUS REFUND — switch to VIP Policy to unlock the +20% bonus");
            bonusBadge.getStyle()
                    .set("display", "inline-block")
                    .set("padding", "8px 14px")
                    .set("border-radius", "999px")
                    .set("background", currentPolicyType == PolicyType.VIP
                            ? "#7c3aed" : "#e9d5ff")
                    .set("color", currentPolicyType == PolicyType.VIP
                            ? "white" : "#6b21a8")
                    .set("font-size", "13px")
                    .set("font-weight", "900")
                    .set("margin-bottom", "14px");
            card.add(bonusBadge);
        }

        // Compute the compensation package for both display and dialog.
        CompensationPackage pkg = vipBenefitService.build(
                currentPolicyType, ctx,
                currentIncludeHalfPriceVoucher,
                currentIncludeFreeDrinkCoupon);

        // Items list (concise summary).
        if (!pkg.isEmpty()) {
            Div list = new Div();
            list.getStyle()
                    .set("display", "flex")
                    .set("flex-direction", "column")
                    .set("gap", "6px")
                    .set("margin-bottom", "14px");
            for (CompensationItem item : pkg.getItems()) {
                Span row = new Span("✓  " + item.getName()
                        + "  —  " + formatMoney(item.getValue()));
                row.getStyle()
                        .set("font-size", "14px")
                        .set("font-weight", "800")
                        .set("color", "#4c1d95");
                list.add(row);
            }
            card.add(list);
        }

        // Details button — opens a Dialog with full voucher metadata.
        Button detailsBtn = new Button("Details →",
                e -> openCompensationDetailsDialog(pkg));
        detailsBtn.getStyle()
                .set("height", "40px")
                .set("background", "white")
                .set("color", "#6b21a8")
                .set("border", "1px solid #a855f7")
                .set("font-weight", "900")
                .set("border-radius", "0")
                .set("padding", "0 22px");
        detailsBtn.setEnabled(!pkg.isEmpty());
        card.add(detailsBtn);

        return card;
    }

    /**
     * Opens a modal dialog listing every voucher / coupon issued by the
     * current decision. Closing the dialog returns the user to the
     * Refund Pending step (the underlying view is never unmounted).
     */
    private void openCompensationDetailsDialog(CompensationPackage pkg) {
        Dialog dialog = new Dialog();
        dialog.setWidth("560px");
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        dialog.getElement().getStyle().set("--lumo-border-radius-l", "18px");

        Div container = new Div();
        container.getStyle()
                .set("padding", "4px 4px 0 4px");

        H3 title = new H3("COMPENSATION DETAILS — " + pkg.getHeadline());
        title.getStyle()
                .set("font-size", "18px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.06em")
                .set("color", "#6b21a8")
                .set("margin", "0 0 18px 0");
        container.add(title);

        if (pkg.isEmpty()) {
            Paragraph empty = new Paragraph(
                    "No vouchers issued for the current decision.");
            empty.getStyle()
                    .set("color", LIGHT_MUTED)
                    .set("font-style", "italic");
            container.add(empty);
        } else {
            for (CompensationItem item : pkg.getItems()) {
                container.add(buildVoucherDetailRow(item));
            }
            // Total face value footer.
            Div footer = new Div();
            footer.getStyle()
                    .set("display", "flex")
                    .set("justify-content", "space-between")
                    .set("align-items", "center")
                    .set("padding", "14px 18px")
                    .set("border-radius", "12px")
                    .set("background", "#7c3aed")
                    .set("color", "white")
                    .set("margin-top", "6px");
            Span fLabel = new Span("TOTAL VOUCHER VALUE");
            fLabel.getStyle().set("font-size", "14px").set("font-weight", "900")
                    .set("letter-spacing", "0.08em");
            Span fValue = new Span(formatMoney(pkg.getTotalValue()));
            fValue.getStyle().set("font-size", "22px").set("font-weight", "900");
            footer.add(fLabel, fValue);
            container.add(footer);
        }

        Button closeBtn = new Button("Back to Refund Pending", e -> dialog.close());
        closeBtn.getStyle()
                .set("margin-top", "18px")
                .set("height", "44px")
                .set("background", BLUE)
                .set("color", "white")
                .set("font-weight", "900")
                .set("border-radius", "0")
                .set("padding", "0 26px")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)");
        Div btnRow = new Div(closeBtn);
        btnRow.getStyle().set("display", "flex").set("justify-content", "flex-end");
        container.add(btnRow);

        dialog.add(container);
        dialog.open();
    }

    /** Renders one voucher row inside the Compensation Details dialog. */
    private Div buildVoucherDetailRow(CompensationItem item) {
        Div row = new Div();
        row.getStyle()
                .set("border", "1px solid #e9d5ff")
                .set("border-radius", "12px")
                .set("padding", "14px 16px")
                .set("margin-bottom", "10px")
                .set("background", "#faf5ff");

        Span name = new Span(item.getName());
        name.getStyle()
                .set("display", "block")
                .set("font-size", "15px")
                .set("font-weight", "900")
                .set("color", "#4c1d95")
                .set("margin-bottom", "4px");

        Paragraph desc = new Paragraph(item.getDescription());
        desc.getStyle()
                .set("font-size", "13px")
                .set("color", "#6b21a8")
                .set("margin", "0 0 10px 0")
                .set("line-height", "1.45");

        Div metaRow = new Div();
        metaRow.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr 1fr")
                .set("gap", "12px");
        metaRow.add(
                voucherMeta("VALUE", formatMoney(item.getValue())),
                voucherMeta("ISSUED", item.getIssueDate().format(DATE_FMT)),
                voucherMeta("EXPIRES", item.getExpiryDate().format(DATE_FMT))
        );

        row.add(name, desc, metaRow);
        return row;
    }

    /** Compact label/value cell for the voucher details dialog. */
    private Div voucherMeta(String label, String value) {
        Div box = new Div();
        Span h = new Span(label);
        h.getStyle()
                .set("display", "block")
                .set("font-size", "11px")
                .set("letter-spacing", "0.08em")
                .set("font-weight", "900")
                .set("color", "#6b21a8")
                .set("margin-bottom", "4px");
        Span v = new Span(value == null || value.isBlank() ? "-" : value);
        v.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("font-weight", "900")
                .set("color", "#4c1d95");
        box.add(h, v);
        return box;
    }

    /**
     * Builds a labelled section block used inside the Refund Decision
     * Panel — a small caps heading on top, the supplied control below.
     */
    private Div decisionSection(String label, com.vaadin.flow.component.Component control) {
        Div section = new Div();
        section.getStyle().set("margin-bottom", "20px");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("display", "block")
                .set("font-size", "13px")
                .set("letter-spacing", "0.08em")
                .set("font-weight", "900")
                .set("color", LIGHT_MUTED)
                .set("margin-bottom", "10px");
        section.add(labelSpan, control);
        return section;
    }

    /** Consistent checkbox styling for the Refund Items selector. */
    private void styleCheckbox(Checkbox cb) {
        cb.getStyle()
                .set("font-weight", "700")
                .set("color", LIGHT_TEXT);
    }

    /**
     * Submits the policy-driven refund: delegates to
     * {@link CancellationService#submitPolicyRefund(String, PolicyType, RefundScope, boolean, boolean, boolean)}
     * which advances the booking to {@code REFUND_PENDING}, cancels any
     * attached food orders (if included) and persists the refund amount
     * on the cancellation record.
     */
    private void submitPolicyRefundAction() {
        try {
            // Persist any reason edits made earlier in the flow.
            cancellationService.updateCancellationReason(bookingReference, currentReason);

            currentBooking = cancellationService.submitPolicyRefund(
                    bookingReference,
                    currentPolicyType,
                    currentScope,
                    true,                       // movie always refunded on step 3
                    currentIncludeFood,
                    currentIncludeVipPackage);
            currentRefundSummary = cancellationService.calculateRefund(bookingReference);
            currentStepIndex = Math.min(currentStepIndex + 1, FLOW.length - 1);

            Notification.show("Refund request submitted under "
                            + currentPolicyType.getDisplayName(),
                            3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            renderCurrentStep();
        } catch (CancellationNotAllowedException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (IllegalStateException ex) {
            Notification.show("Status transition failed: " + ex.getMessage(),
                            6000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (RuntimeException ex) {
            Notification.show("Refund submission failed: " + ex.getMessage(),
                            7000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
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
        } catch (IllegalStateException ex) {
            // Booking.transitionTo throws IllegalStateException when the underlying
            // status machine rejects a move. Surface the message instead of letting
            // Vaadin swallow it, otherwise the user just sees an unresponsive button.
            Notification.show("Status transition failed: " + ex.getMessage(),
                            6000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (RuntimeException ex) {
            // Any persistence-level failure (e.g. JDBC CHECK constraint violation
            // when an old booking_status enum CHECK still lingers in H2) must not
            // silently break the flow.
            Notification.show("Refund step failed: " + ex.getMessage(),
                            7000, Notification.Position.MIDDLE)
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
