package com.eduaccess.ui;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.service.CancellationService;
import com.eduaccess.service.LoginService;
import com.eduaccess.service.batch.BatchCancellationService;
import com.eduaccess.service.batch.BatchOperationRecord;
import com.eduaccess.service.batch.BatchRefundResult;
import com.eduaccess.service.email.EmailReceiptService;
import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundScope;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Batch Cancellation Dashboard.
 * <p>
 * Batch refunds are <strong>only</strong> used for emergency situations
 * (cinema-wide outage, equipment failure, …). The dashboard therefore
 * exposes a single fixed policy:
 * <ul>
 *   <li><b>Emergency Policy</b> — the only choice.</li>
 *   <li><b>Refund Scope</b> — Partial (50%) or Full (100%).</li>
 *   <li><b>Refund Items</b> — Movie Ticket and/or Food Combo / Drinks.</li>
 *   <li><b>Compensation</b> — Half-price Voucher and/or Free Drink Coupon,
 *       applied to <strong>every</strong> impacted customer (VIP flag is
 *       irrelevant for batch flows).</li>
 * </ul>
 * <p>
 * Two action buttons are exposed: <b>Preview Email</b> renders an inline
 * confirmation message in English directly on the page, and
 * <b>Batch Refund</b> executes the operation. Each successful row goes
 * through the existing
 * {@link CancellationService#submitPolicyRefund} pipeline, so Refund History
 * (CancellationRecord), Audit Log and Email Management all update
 * automatically.
 */
@CssImport("./styles/backoffice-pro.css")
@Route(value = "cancellation-batch", layout = MainLayout.class)
@PageTitle("HCBS — Batch Cancellation")
public class BatchCancellationDashboardView extends Div implements BeforeEnterObserver {

    // ── Theme tokens ─────────────────────────────────────────────────────
    private static final String DARK_BG = "#020b1d";
    private static final String BLUE = "#0072ce";
    private static final String LIGHT_TEXT = "#142033";
    private static final String LIGHT_MUTED = "#64748b";
    private static final String CARD_BORDER = "rgba(15,23,42,0.08)";
    private static final String SUCCESS_GREEN = "#16a34a";
    private static final String DANGER_RED = "#dc2626";
    private static final String AMBER = "#f59e0b";

    private static final DateTimeFormatter STAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CancellationService cancellationService;
    private final BatchCancellationService batchCancellationService;
    private final EmailReceiptService emailReceiptService;
    private final LoginService loginService;

    // ── State ────────────────────────────────────────────────────────────
    private final List<Booking> allBookings = new ArrayList<>();
    private final Set<Booking> selectedBookings = new LinkedHashSet<>();
    /** Last preview snapshot used to drive Email Preview / Receipt Preview. */
    private BatchOperationRecord previewSnapshot;
    /** Last actual execution snapshot (used by Receipt Preview after a refund). */
    private BatchOperationRecord executionSnapshot;

    // ── Components ───────────────────────────────────────────────────────
    private final Grid<Booking> bookingGrid = new Grid<>(Booking.class, false);
    private ListDataProvider<Booking> dataProvider;

    private final RadioButtonGroup<RefundScope> scopeGroup = new RadioButtonGroup<>();
    private final Checkbox includeMovieBox = new Checkbox("Movie Ticket");
    private final Checkbox includeFoodBox = new Checkbox("Food Combo / Drinks");
    private final Checkbox includeHalfPriceVoucherBox = new Checkbox("Half-price Voucher");
    private final Checkbox includeFreeDrinkBox = new Checkbox("Free Drink Coupon");

    private final Div emailInlineHolder = new Div();
    private final Div resultHolder = new Div();

    private final Button previewEmailButton = new Button("Preview Email");
    private final Button batchRefundButton = new Button("Batch Refund");
    private final Button openEmailManagementButton =
            new Button("Open Email Management");

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkCancellationAccess(event, loginService);
    }

    public BatchCancellationDashboardView(CancellationService cancellationService,
                                          BatchCancellationService batchCancellationService,
                                          EmailReceiptService emailReceiptService,
                                          LoginService loginService) {
        this.cancellationService = cancellationService;
        this.batchCancellationService = batchCancellationService;
        this.emailReceiptService = emailReceiptService;
        this.loginService = loginService;
        this.dataProvider = new ListDataProvider<>(allBookings);

        setWidthFull();
        addClassName("batch-cancellation-pro-page");
        getStyle()
                .set("background", DARK_BG)
                .set("min-height", "100vh")
                .set("color", "white");

        Div container = new Div();
        container.getStyle()
                .set("max-width", "1480px")
                .set("margin", "0 auto")
                .set("padding", "44px 48px")
                .set("box-sizing", "border-box");

        container.add(buildHeader());
        container.add(buildLayout());
        container.add(buildEmailInlineSection());
        container.add(buildResultSection());

        add(container);

        wireActions();
        refreshBookings();
        updateActionAvailability();
    }

    // ── Header ───────────────────────────────────────────────────────────

    private Div buildHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("margin-bottom", "32px")
                .set("flex-wrap", "wrap")
                .set("gap", "16px");

        Div titleBlock = new Div();
        Span breadcrumb = new Span("CANCELLATION → BATCH MODE");
        breadcrumb.getStyle()
                .set("display", "block")
                .set("font-size", "12px")
                .set("font-weight", "800")
                .set("letter-spacing", "0.18em")
                .set("color", "rgba(255,255,255,0.55)")
                .set("margin-bottom", "6px");
        H2 title = new H2("Batch Cancellation Dashboard");
        title.getStyle().set("margin", "0").set("font-weight", "950");
        Paragraph subtitle = new Paragraph(
                "Emergency batch refunds — applied uniformly to every selected booking, "
                        + "regardless of the customer's VIP tier.");
        subtitle.getStyle()
                .set("margin", "8px 0 0 0")
                .set("color", "rgba(255,255,255,0.65)")
                .set("font-size", "13px");
        titleBlock.add(breadcrumb, title, subtitle);

        Button back = new Button("← Back to Cancellation");
        back.getStyle()
                .set("background", "rgba(255,255,255,0.08)")
                .set("color", "white")
                .set("font-weight", "700")
                .set("padding", "0 18px")
                .set("height", "42px")
                .set("border", "1px solid rgba(255,255,255,0.18)");
        back.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate("cancellation")));

        header.add(titleBlock, back);
        return header;
    }

    // ── Layout ───────────────────────────────────────────────────────────

    private Div buildLayout() {
        Div layout = new Div();
        layout.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "minmax(0,1fr) 420px")
                .set("gap", "32px")
                .set("align-items", "start");

        layout.add(buildGridCard(), buildControlPanel());
        return layout;
    }

    private Div buildGridCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", "white")
                .set("color", LIGHT_TEXT)
                .set("border-radius", "8px")
                .set("padding", "0")
                .set("overflow", "hidden")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.3)")
                .set("border", "1px solid " + CARD_BORDER);

        Div toolbar = new Div();
        toolbar.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("padding", "16px 20px")
                .set("background", "#0f172a")
                .set("color", "white")
                .set("font-weight", "700");
        Span left = new Span("BOOKING GRID — multi-select supported");
        left.getStyle().set("letter-spacing", "0.12em").set("font-size", "12px");
        Button selectAll = new Button("Select All Cancellable", e -> selectAllCancellable());
        selectAll.getStyle()
                .set("background", "rgba(255,255,255,0.12)")
                .set("color", "white")
                .set("font-weight", "700")
                .set("border", "1px solid rgba(255,255,255,0.2)");
        Button clearSel = new Button("Clear Selection", e -> bookingGrid.deselectAll());
        clearSel.getStyle()
                .set("background", "transparent")
                .set("color", "white")
                .set("font-weight", "600")
                .set("border", "1px solid rgba(255,255,255,0.2)");
        Div btns = new Div(selectAll, clearSel);
        btns.getStyle().set("display", "flex").set("gap", "8px");
        toolbar.add(left, btns);

        configureGrid();

        card.add(toolbar, bookingGrid);
        return card;
    }

    private void configureGrid() {
        bookingGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        bookingGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

        bookingGrid.addColumn(Booking::getBookingReference)
                .setHeader("Reference").setAutoWidth(true).setFlexGrow(1);
        bookingGrid.addColumn(b -> {
                    if (b.getScreening() != null && b.getScreening().getFilm() != null) {
                        return b.getScreening().getFilm().getTitle();
                    }
                    return "Unknown Film";
                })
                .setHeader("Film").setAutoWidth(true).setFlexGrow(2);
        bookingGrid.addColumn(Booking::getCustomerName)
                .setHeader("Customer").setAutoWidth(true).setFlexGrow(1);
        bookingGrid.addComponentColumn(this::renderStatusBadge)
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        bookingGrid.addColumn(b -> formatMoney(b.getTotalCost()))
                .setHeader("Paid").setAutoWidth(true).setFlexGrow(0);

        bookingGrid.setDataProvider(dataProvider);
        bookingGrid.setHeight("640px");

        bookingGrid.asMultiSelect().addValueChangeListener(e -> {
            selectedBookings.clear();
            selectedBookings.addAll(e.getValue());
            updateActionAvailability();
        });
    }

    private Span renderStatusBadge(Booking booking) {
        BookingStatus status = booking.getStatus();
        Span badge = new Span(status.getDisplayName());
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "3px 10px")
                .set("border-radius", "999px")
                .set("background", status.getBadgeBackground())
                .set("color", status.getBadgeTextColor())
                .set("font-size", "12px")
                .set("font-weight", "800");
        return badge;
    }

    // ── Control Panel ────────────────────────────────────────────────────

    private Div buildControlPanel() {
        Div panel = new Div();
        panel.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "20px")
                .set("position", "sticky")
                .set("top", "24px");
        panel.add(buildPolicyCard(), buildActionsCard());
        return panel;
    }

    private Div buildPolicyCard() {
        Div card = whiteCard();
        card.add(sectionHeading("REFUND POLICY",
                "Emergency Policy is the only option for batch refunds. "
                        + "Compensation applies to every customer (VIP and non-VIP)."));

        // Locked policy badge
        Div policyBadge = new Div();
        policyBadge.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("gap", "10px")
                .set("padding", "10px 14px")
                .set("background", "rgba(220,38,38,0.08)")
                .set("border", "1.5px solid " + DANGER_RED)
                .set("border-radius", "8px")
                .set("margin-bottom", "18px")
                .set("color", LIGHT_TEXT)
                .set("font-weight", "800");
        Span dot = new Span("●");
        dot.getStyle().set("color", DANGER_RED).set("font-size", "16px");
        Span txt = new Span("Emergency Policy (locked) — used for emergency batch refunds only.");
        txt.getStyle().set("font-size", "13px");
        policyBadge.add(dot, txt);
        card.add(policyBadge);

        // Scope (Partial / Full)
        scopeGroup.setItems(RefundScope.PARTIAL, RefundScope.FULL);
        scopeGroup.setItemLabelGenerator(s -> s == RefundScope.FULL
                ? "Full Refund (100%)" : "Partial Refund (50%)");
        scopeGroup.setValue(RefundScope.PARTIAL);
        scopeGroup.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "4px");
        Div scopeBlock = labelledBlock("Refund Scope", scopeGroup);

        // Items (Movie / Food)
        Div items = new Div();
        items.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "6px");
        styleCheckbox(includeMovieBox);
        styleCheckbox(includeFoodBox);
        includeMovieBox.setValue(true);
        items.add(includeMovieBox, includeFoodBox);
        Div itemsBlock = labelledBlock("Refund Items", items);

        // Compensation (applies to ALL customers)
        Div compensation = new Div();
        compensation.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "6px");
        styleCheckbox(includeHalfPriceVoucherBox);
        styleCheckbox(includeFreeDrinkBox);
        includeHalfPriceVoucherBox.setValue(true);
        compensation.add(includeHalfPriceVoucherBox, includeFreeDrinkBox);
        Div compBlock = labelledBlock("Compensation (applied to every customer)", compensation);

        Div body = new Div(scopeBlock, itemsBlock, compBlock);
        body.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "18px");
        card.add(body);
        return card;
    }

    private Div buildActionsCard() {
        Div card = whiteCard();
        card.add(sectionHeading("ACTIONS",
                "Preview the customer email or execute the batch refund. "
                        + "After execution, each generated email and receipt is available "
                        + "in the Email Management screen for archival and follow-up."));

        previewEmailButton.getStyle()
                .set("width", "100%").set("height", "44px")
                .set("background", "white").set("color", BLUE)
                .set("font-weight", "800").set("border", "1.5px solid " + BLUE);

        batchRefundButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        batchRefundButton.getStyle()
                .set("width", "100%").set("height", "50px")
                .set("background", DANGER_RED).set("color", "white")
                .set("font-weight", "900").set("font-size", "15px")
                .set("letter-spacing", "0.05em");

        openEmailManagementButton.getStyle()
                .set("width", "100%").set("height", "40px")
                .set("background", "transparent").set("color", "#7c3aed")
                .set("font-weight", "700").set("border", "1px dashed #7c3aed");

        Div actions = new Div(previewEmailButton, batchRefundButton,
                openEmailManagementButton);
        actions.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "10px");
        card.add(actions);
        return card;
    }

    // ── Inline Email Section (TASK 11 §5 revised) ────────────────────────

    private Div buildEmailInlineSection() {
        emailInlineHolder.getStyle()
                .set("margin-top", "32px");
        return emailInlineHolder;
    }

    private void renderInlineEmails(BatchOperationRecord rec) {
        emailInlineHolder.removeAll();
        if (rec == null || rec.getEntries().isEmpty()) {
            return;
        }
        Div card = whiteCard();
        H3 title = new H3("EMERGENCY REFUND CONFIRMATION");
        title.getStyle().set("margin", "0").set("color", DANGER_RED).set("font-weight", "950");
        Paragraph subtitle = new Paragraph(
                "The following message will be delivered to every impacted customer. "
                        + "Refunds have been processed and compensation has been issued.");
        subtitle.getStyle().set("color", LIGHT_MUTED).set("font-size", "13px")
                .set("margin", "6px 0 18px 0").set("font-style", "italic");
        card.add(title, subtitle);

        Div list = new Div();
        list.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "12px");
        for (BatchRefundResult row : rec.getEntries()) {
            if (!row.isSuccess()) continue;
            list.add(renderInlineEmail(row, rec));
        }
        if (!list.getElement().getChildren().findAny().isPresent()) {
            Span empty = new Span("No successful rows in this batch — nothing to confirm.");
            empty.getStyle().set("color", LIGHT_MUTED).set("font-style", "italic");
            list.add(empty);
        }
        card.add(list);
        emailInlineHolder.add(card);
    }

    private Div renderInlineEmail(BatchRefundResult row, BatchOperationRecord rec) {
        Div msg = new Div();
        msg.addClassName("batch-inline-email");
        msg.getStyle()
                .set("background", "#fff7ed")
                .set("border-left", "4px solid " + AMBER)
                .set("padding", "16px 20px")
                .set("border-radius", "8px")
                .set("color", LIGHT_TEXT);

        Span heading = new Span("Dear " + (row.getCustomerName() == null
                ? "Valued Customer" : row.getCustomerName()) + ",");
        heading.getStyle().set("display", "block").set("font-weight", "900")
                .set("margin-bottom", "6px");
        Paragraph body = new Paragraph(
                "Due to an emergency situation at our cinema, your booking "
                        + row.getBookingReference()
                        + " has been cancelled by our staff. We have already processed a "
                        + (rec.getScope() == RefundScope.FULL ? "full" : "partial")
                        + " refund of " + formatMoney(row.getRefundAmount())
                        + " back to your original payment method (5 business days), "
                        + "and the compensation listed below has been deposited into your "
                        + "HCBS account immediately. We sincerely apologise for the inconvenience.");
        body.getStyle().set("margin", "0 0 8px 0").set("font-size", "13.5px")
                .set("line-height", "1.7");

        Div compInfo = new Div();
        compInfo.getStyle().set("display", "flex").set("gap", "12px")
                .set("flex-wrap", "wrap").set("font-size", "12.5px")
                .set("color", LIGHT_MUTED).set("margin-bottom", "4px");
        compInfo.add(chip("Refund: " + formatMoney(row.getRefundAmount()), SUCCESS_GREEN));
        compInfo.add(chip("Compensation: "
                        + (row.getCompensationItems().isEmpty() ? "None"
                        : formatMoney(row.getCompensationValue())
                        + " (" + row.getCompensationItems().size() + " item)"),
                "#7c3aed"));
        compInfo.add(chip("Booking: " + row.getBookingReference(), BLUE));

        Span signature = new Span(
                "Refund processed and compensation issued. — HCBS Cinemas Customer Service");
        signature.getStyle().set("display", "block").set("margin-top", "8px")
                .set("font-size", "12.5px").set("font-weight", "700")
                .set("color", DANGER_RED);

        msg.add(heading, body, compInfo, signature);
        return msg;
    }

    private Span chip(String text, String color) {
        Span s = new Span(text);
        s.addClassName("batch-inline-email-chip");
        s.getStyle()
                .set("padding", "4px 10px")
                .set("background", "white")
                .set("border", "1px solid " + color)
                .set("border-radius", "999px")
                .set("color", color)
                .set("font-weight", "800");
        return s;
    }

    // ── Result section (Refund History summary inside the page) ──────────

    private Div buildResultSection() {
        resultHolder.removeAll();
        resultHolder.getStyle().set("margin-top", "32px");
        return resultHolder;
    }

    private void renderResult(BatchOperationRecord rec) {
        resultHolder.removeAll();
        if (rec == null) return;
        Div card = whiteCard();
        Div header = new Div();
        header.getStyle().set("display", "flex").set("align-items", "center")
                .set("justify-content", "space-between").set("margin-bottom", "18px");
        H3 heading = new H3("BATCH REFUND EXECUTED");
        heading.getStyle().set("margin", "0").set("color", SUCCESS_GREEN)
                .set("font-weight", "900");
        Span meta = new Span("OP-" + rec.getOperationId().substring(0, 8).toUpperCase()
                + "  •  " + rec.getExecutedAt().format(STAMP_FMT)
                + "  •  by " + rec.getOperatorUsername());
        meta.getStyle().set("font-size", "12px").set("font-weight", "700")
                .set("color", LIGHT_MUTED).set("letter-spacing", "0.06em");
        header.add(heading, meta);
        card.add(header);

        Div totals = new Div();
        totals.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(4, minmax(0,1fr))")
                .set("gap", "16px").set("margin-bottom", "18px");
        totals.add(metricTile("Selected", String.valueOf(rec.getTotalSelectedOrders()), BLUE));
        totals.add(metricTile("Total Refund", formatMoney(rec.getTotalRefundAmount()), SUCCESS_GREEN));
        totals.add(metricTile("Compensation", formatMoney(rec.getTotalCompensationValue()), "#7c3aed"));
        totals.add(metricTile("Vouchers Issued", String.valueOf(rec.getTotalVoucherCount()), AMBER));
        card.add(totals);

        Div list = new Div();
        list.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "10px");
        for (BatchRefundResult row : rec.getEntries()) {
            list.add(renderResultRow(row));
        }
        card.add(list);
        resultHolder.add(card);
    }

    private Div renderResultRow(BatchRefundResult row) {
        Div r = new Div();
        r.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "160px 1fr 140px 140px")
                .set("gap", "14px").set("align-items", "center")
                .set("padding", "14px 16px")
                .set("background", row.isSuccess() ? "#f0fdf4" : "#fef2f2")
                .set("border", "1px solid " + (row.isSuccess() ? "#bbf7d0" : "#fecaca"))
                .set("border-radius", "8px");
        Span ref = new Span(row.getBookingReference());
        ref.getStyle().set("font-weight", "900").set("color", LIGHT_TEXT);
        Span info = new Span(row.getCustomerName()
                + (row.getCustomerEmail() == null ? "" : " — " + row.getCustomerEmail()));
        info.getStyle().set("color", LIGHT_MUTED).set("font-size", "13px");
        Span money = new Span(row.isSuccess() ? formatMoney(row.getRefundAmount())
                : (row.getErrorMessage() == null ? "Skipped" : row.getErrorMessage()));
        money.getStyle().set("font-weight", "800")
                .set("color", row.isSuccess() ? SUCCESS_GREEN : DANGER_RED);
        Span vouchers = new Span(row.isSuccess()
                ? (row.getVoucherCount() + " voucher(s)") : "—");
        vouchers.getStyle().set("color", LIGHT_MUTED).set("font-size", "13px")
                .set("text-align", "right");
        r.add(ref, info, money, vouchers);
        return r;
    }

    private Div metricTile(String label, String value, String accent) {
        Div tile = new Div();
        tile.getStyle().set("padding", "14px 16px").set("background", "#f8fafc")
                .set("border-left", "4px solid " + accent).set("border-radius", "8px");
        Span lab = new Span(label);
        lab.getStyle().set("display", "block").set("font-size", "11px")
                .set("font-weight", "800").set("letter-spacing", "0.08em")
                .set("color", LIGHT_MUTED).set("margin-bottom", "4px");
        Span val = new Span(value);
        val.getStyle().set("font-size", "22px").set("font-weight", "900").set("color", LIGHT_TEXT);
        tile.add(lab, val);
        return tile;
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private void wireActions() {
        previewEmailButton.addClickListener(e -> {
            BatchOperationRecord snap = computePreview();
            if (snap == null) return;
            renderInlineEmails(snap);
            Notification.show("Preview rendered below — review the message before executing.",
                            2500, Notification.Position.TOP_END)
                    .addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        });

        batchRefundButton.addClickListener(e -> confirmThenExecute());

        // Cross-page hand-off — every email/receipt produced by the batch
        // flow lands in the Email Management dashboard automatically through
        // EmailReceiptService → EmailLogService.
        openEmailManagementButton.addClickListener(e ->
                getUI().ifPresent(ui -> ui.navigate("email-management")));
    }

    private BatchOperationRecord computePreview() {
        if (selectedBookings.isEmpty()) {
            Notification.show("Select at least one booking to continue.",
                            2500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            return null;
        }
        previewSnapshot = batchCancellationService.previewBatch(
                new ArrayList<>(selectedBookings),
                PolicyType.EMERGENCY,
                scopeGroup.getValue() == null ? RefundScope.PARTIAL : scopeGroup.getValue(),
                includeMovieBox.getValue(),
                includeFoodBox.getValue(),
                /* VIP package is irrelevant for batch */ false,
                includeHalfPriceVoucherBox.getValue(),
                includeFreeDrinkBox.getValue());
        return previewSnapshot;
    }

    private void updateActionAvailability() {
        // Keep the three action buttons visibly clickable. The action methods
        // already validate selection and show a friendly notification when
        // no booking is selected, so disabling the buttons here only makes the
        // UI look broken after the dark-theme styling.
        previewEmailButton.setEnabled(true);
        batchRefundButton.setEnabled(true);
        openEmailManagementButton.setEnabled(true);
    }

    private void confirmThenExecute() {
        if (selectedBookings.isEmpty()) {
            Notification.show("Select at least one booking to refund.",
                            2500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            return;
        }
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Execute Batch Refund?");
        confirm.setWidth("460px");
        Div body = new Div();
        body.getStyle().set("color", LIGHT_TEXT).set("font-size", "14px")
                .set("line-height", "1.6");
        body.setText("This will advance " + selectedBookings.size()
                + " booking(s) to REFUND_PENDING using the Emergency Policy ("
                + (scopeGroup.getValue() == RefundScope.FULL ? "Full 100%" : "Partial 50%")
                + "). Cancellation Records and Audit Log entries will be written automatically.");
        confirm.add(body);
        Button cancel = new Button("Cancel", ev -> confirm.close());
        Button proceed = new Button("Execute", ev -> { confirm.close(); executeBatch(); });
        proceed.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirm.getFooter().add(cancel, proceed);
        confirm.open();
    }

    private void executeBatch() {
        try {
            executionSnapshot = batchCancellationService.executeBatch(
                    new ArrayList<>(selectedBookings),
                    PolicyType.EMERGENCY,
                    scopeGroup.getValue() == null ? RefundScope.PARTIAL : scopeGroup.getValue(),
                    includeMovieBox.getValue(),
                    includeFoodBox.getValue(),
                    /* VIP package */ false,
                    includeHalfPriceVoucherBox.getValue(),
                    includeFreeDrinkBox.getValue());
            // Eagerly materialise the customer-facing artefacts so the Email
            // Management dashboard is populated even if the operator never
            // opens a preview dialog from this page.
            emailReceiptService.buildBatchEmails(executionSnapshot);
            emailReceiptService.buildBatchReceipts(executionSnapshot);

            renderResult(executionSnapshot);
            renderInlineEmails(executionSnapshot);
            Notification ok = Notification.show("Batch refund executed — "
                            + executionSnapshot.getSuccessCount() + " succeeded, "
                            + executionSnapshot.getFailureCount() + " skipped. "
                            + "Emails and receipts are now available in Email Management.",
                    5000, Notification.Position.TOP_CENTER);
            ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            bookingGrid.deselectAll();
            refreshBookings();
            updateActionAvailability();
        } catch (Exception ex) {
            Notification.show("Batch failed: " + ex.getMessage(),
                            5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    // ── Data + helpers ───────────────────────────────────────────────────

    private void refreshBookings() {
        allBookings.clear();
        cancellationService.findAllBookings().stream()
                .filter(b -> b.getStatus() != BookingStatus.REFUNDED)
                .forEach(allBookings::add);
        dataProvider.refreshAll();
        selectedBookings.removeIf(sel -> !allBookings.contains(sel));
    }

    private void selectAllCancellable() {
        Set<Booking> targets = allBookings.stream()
                .filter(b -> !b.getStatus().isTerminal())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        bookingGrid.asMultiSelect().setValue(targets);
    }

    private void styleCheckbox(Checkbox box) {
        box.getStyle().set("font-weight", "700").set("color", LIGHT_TEXT);
    }

    private Div whiteCard() {
        Div card = new Div();
        card.getStyle().set("background", "white").set("color", LIGHT_TEXT)
                .set("padding", "22px 24px").set("border-radius", "8px")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.3)")
                .set("border", "1px solid " + CARD_BORDER);
        return card;
    }

    private Div sectionHeading(String headline, String subtitle) {
        Div header = new Div();
        header.getStyle().set("margin-bottom", "16px");
        H3 h = new H3(headline);
        h.getStyle().set("margin", "0").set("color", BLUE).set("font-weight", "900")
                .set("letter-spacing", "0.06em");
        Span s = new Span(subtitle);
        s.getStyle().set("display", "block").set("font-size", "12px")
                .set("color", LIGHT_MUTED).set("font-style", "italic");
        header.add(h, s);
        return header;
    }

    private Div labelledBlock(String label, com.vaadin.flow.component.Component body) {
        Div block = new Div();
        Span lab = new Span(label);
        lab.getStyle().set("display", "block").set("font-size", "11px")
                .set("font-weight", "800").set("letter-spacing", "0.08em")
                .set("color", LIGHT_MUTED).set("margin-bottom", "8px")
                .set("text-transform", "uppercase");
        block.add(lab, body);
        return block;
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.UK)
                .format(amount == null ? BigDecimal.ZERO : amount);
    }
}
