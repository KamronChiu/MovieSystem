package com.eduaccess.ui;

import com.eduaccess.service.LoginService;
import com.eduaccess.service.email.EmailLogEntry;
import com.eduaccess.service.email.EmailLogService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Email Management dashboard.
 * <p>
 * Single source of truth for every cancellation email and receipt the
 * system has produced. The page is intentionally enterprise-flavoured:
 * it pairs a metric strip, filter toolbar, master grid, and a detail
 * pane with template management and delivery-status controls.
 * <p>
 * Both single-cancellation and batch-cancellation flows write into the
 * same {@link EmailLogService}, so this screen always reflects the
 * latest activity from both pipelines.
 */
@CssImport("./styles/backoffice-pro.css")
@Route(value = "email-management", layout = MainLayout.class)
@PageTitle("HCBS — Email Management")
public class EmailManagementView extends Div implements BeforeEnterObserver {

    // ── Theme tokens (kept consistent with Cancellation / Refund History) ──
    private static final String DARK_BG     = "#020b1d";
    private static final String BLUE        = "#0072ce";
    private static final String LIGHT_TEXT  = "#142033";
    private static final String LIGHT_MUTED = "#64748b";
    private static final String CARD_BORDER = "rgba(15,23,42,0.08)";
    private static final String SUCCESS     = "#16a34a";
    private static final String AMBER       = "#f59e0b";
    private static final String DANGER      = "#dc2626";
    private static final String VIP_PURPLE  = "#7c3aed";

    private static final DateTimeFormatter STAMP_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.UK);
    private static final NumberFormat MONEY_FMT =
            NumberFormat.getCurrencyInstance(Locale.UK);

    private final EmailLogService emailLogService;
    private final LoginService loginService;

    // ── Data + components ────────────────────────────────────────────────
    private final List<EmailLogEntry> rows = new ArrayList<>();
    private final ListDataProvider<EmailLogEntry> dataProvider =
            new ListDataProvider<>(rows);
    private final Grid<EmailLogEntry> grid = new Grid<>(EmailLogEntry.class, false);

    private final ComboBox<EmailLogEntry.Source> sourceFilter = new ComboBox<>();
    private final ComboBox<EmailLogEntry.Status> statusFilter = new ComboBox<>();
    private final TextField keywordFilter = new TextField();
    private final Button refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
    private final Button clearButton   = new Button("Clear Filters");

    private final Span totalChip   = new Span();
    private final Span sentChip    = new Span();
    private final Span pendingChip = new Span();
    private final Span failedChip  = new Span();

    private final Div detailHolder = new Div();
    private EmailLogEntry selectedEntry;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Same gate as Cancellation / Refund History — staff only.
        PermissionChecker.checkCancellationAccess(event, loginService);
    }

    public EmailManagementView(EmailLogService emailLogService,
                               LoginService loginService) {
        this.emailLogService = emailLogService;
        this.loginService = loginService;

        setWidthFull();
        addClassName("email-management-pro-page");
        getStyle()
                .set("background", DARK_BG)
                .set("min-height", "100vh")
                .set("color", "white");

        Div container = new Div();
        container.getStyle()
                .set("max-width", "1520px")
                .set("margin", "0 auto")
                .set("padding", "44px 48px")
                .set("box-sizing", "border-box");

        container.add(buildHeader(),
                buildMetricsStrip(),
                buildLayout(),
                buildTemplateSection());

        add(container);

        wireListeners();
        refresh();
    }

    // ── Header ───────────────────────────────────────────────────────────

    private Div buildHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex").set("align-items", "center")
                .set("justify-content", "space-between")
                .set("margin-bottom", "28px").set("flex-wrap", "wrap").set("gap", "16px");

        Div titleBlock = new Div();
        Span crumb = new Span("BACK-OFFICE → EMAIL MANAGEMENT");
        crumb.getStyle()
                .set("display", "block").set("font-size", "12px")
                .set("font-weight", "800").set("letter-spacing", "0.18em")
                .set("color", "rgba(255,255,255,0.55)").set("margin-bottom", "6px");
        H2 title = new H2("Email Management");
        title.getStyle().set("margin", "0").set("font-weight", "950");
        Paragraph subtitle = new Paragraph(
                "Central inbox for every cancellation email and refund receipt — "
                        + "both single transactions and emergency batch operations. Preview "
                        + "messages, manage templates, and track delivery status from one place.");
        subtitle.getStyle()
                .set("margin", "8px 0 0 0").set("color", "rgba(255,255,255,0.65)")
                .set("font-size", "13px").set("max-width", "780px");
        titleBlock.add(crumb, title, subtitle);

        Button back = new Button("← Back to Cancellation");
        back.getStyle()
                .set("background", "rgba(255,255,255,0.08)").set("color", "white")
                .set("font-weight", "700").set("padding", "0 18px").set("height", "42px")
                .set("border", "1px solid rgba(255,255,255,0.18)");
        back.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate("cancellation")));

        header.add(titleBlock, back);
        return header;
    }

    // ── Metric tiles ─────────────────────────────────────────────────────

    private Div buildMetricsStrip() {
        Div strip = new Div();
        strip.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(4, minmax(0,1fr))")
                .set("gap", "16px").set("margin-bottom", "24px");

        strip.add(metricTile("Total Messages", totalChip,   BLUE,    VaadinIcon.ENVELOPE_O));
        strip.add(metricTile("Sent",           sentChip,    SUCCESS, VaadinIcon.CHECK_CIRCLE));
        strip.add(metricTile("Pending",        pendingChip, AMBER,   VaadinIcon.CLOCK));
        strip.add(metricTile("Failed",         failedChip,  DANGER,  VaadinIcon.WARNING));
        return strip;
    }

    private Div metricTile(String label, Span valueChip, String accent, VaadinIcon iconKind) {
        Div tile = new Div();
        tile.getStyle()
                .set("background", "white").set("color", LIGHT_TEXT)
                .set("border-radius", "10px").set("padding", "18px 20px")
                .set("border-left", "5px solid " + accent)
                .set("display", "flex").set("align-items", "center").set("gap", "16px")
                .set("box-shadow", "0 10px 28px rgba(0,0,0,0.28)");

        Icon icon = new Icon(iconKind);
        icon.setSize("28px");
        icon.getStyle().set("color", accent);

        Div text = new Div();
        Span lab = new Span(label);
        lab.getStyle()
                .set("display", "block").set("font-size", "11px")
                .set("font-weight", "800").set("letter-spacing", "0.08em")
                .set("color", LIGHT_MUTED).set("margin-bottom", "4px")
                .set("text-transform", "uppercase");
        valueChip.getStyle()
                .set("font-size", "26px").set("font-weight", "900").set("color", LIGHT_TEXT);
        text.add(lab, valueChip);

        tile.add(icon, text);
        return tile;
    }

    // ── Master / detail layout ───────────────────────────────────────────

    private Div buildLayout() {
        Div layout = new Div();
        layout.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "minmax(0,1fr) 460px")
                .set("gap", "24px").set("align-items", "start");
        layout.add(buildGridCard(), buildDetailCard());
        return layout;
    }

    private Div buildGridCard() {
        Div card = whiteCard();
        card.getStyle().set("padding", "0").set("overflow", "hidden");
        card.add(buildToolbar(), buildGrid());
        return card;
    }

    private Div buildToolbar() {
        Div bar = new Div();
        bar.getStyle()
                .set("display", "flex").set("flex-wrap", "wrap").set("gap", "12px")
                .set("align-items", "flex-end").set("padding", "18px 20px")
                .set("background", "#0f172a").set("color", "white");

        sourceFilter.setLabel("Source");
        sourceFilter.setItems(EmailLogEntry.Source.values());
        sourceFilter.setItemLabelGenerator(EmailLogEntry.Source::getDisplayName);
        sourceFilter.setPlaceholder("All sources");
        sourceFilter.setClearButtonVisible(true);
        sourceFilter.getStyle().set("min-width", "180px");

        statusFilter.setLabel("Delivery Status");
        statusFilter.setItems(EmailLogEntry.Status.values());
        statusFilter.setItemLabelGenerator(EmailLogEntry.Status::getDisplayName);
        statusFilter.setPlaceholder("All statuses");
        statusFilter.setClearButtonVisible(true);
        statusFilter.getStyle().set("min-width", "180px");

        keywordFilter.setLabel("Search");
        keywordFilter.setPlaceholder("Booking, customer or subject…");
        keywordFilter.setValueChangeMode(ValueChangeMode.LAZY);
        keywordFilter.getStyle().set("min-width", "260px");
        keywordFilter.setClearButtonVisible(true);

        styleToolbarButton(refreshButton, BLUE);
        styleToolbarButton(clearButton, "rgba(255,255,255,0.10)");

        bar.add(sourceFilter, statusFilter, keywordFilter, refreshButton, clearButton);
        return bar;
    }

    private void styleToolbarButton(Button button, String background) {
        button.getStyle()
                .set("height", "36px").set("padding", "0 16px")
                .set("font-weight", "800").set("color", "white")
                .set("background", background)
                .set("border", "1px solid rgba(255,255,255,0.18)")
                .set("border-radius", "8px");
    }

    private Grid<EmailLogEntry> buildGrid() {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.setHeight("560px");
        grid.setDataProvider(dataProvider);

        grid.addColumn(e -> e.getCreatedAt() == null ? "—"
                        : e.getCreatedAt().format(STAMP_FMT))
                .setHeader("Sent").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::renderSourceBadge)
                .setHeader("Source").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(EmailLogEntry::getBookingReference)
                .setHeader("Booking").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(EmailLogEntry::getCustomerName)
                .setHeader("Customer").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(EmailLogEntry::getCustomerEmail)
                .setHeader("Email").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(e -> formatMoney(e.getRefundAmount()))
                .setHeader("Refund").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::renderStatusBadge)
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);

        grid.asSingleSelect().addValueChangeListener(e -> {
            selectedEntry = e.getValue();
            renderDetail();
        });
        return grid;
    }

    private Span renderSourceBadge(EmailLogEntry entry) {
        boolean batch = entry.getSource() == EmailLogEntry.Source.BATCH;
        Span badge = new Span(batch ? "Batch" : "Single");
        badge.getStyle()
                .set("display", "inline-block").set("padding", "3px 10px")
                .set("border-radius", "999px")
                .set("background", batch ? "rgba(220,38,38,0.10)" : "rgba(0,114,206,0.10)")
                .set("color", batch ? DANGER : BLUE)
                .set("font-size", "12px").set("font-weight", "800");
        return badge;
    }

    private Span renderStatusBadge(EmailLogEntry entry) {
        EmailLogEntry.Status s = entry.getStatus();
        Span badge = new Span(s.getDisplayName());
        badge.getStyle()
                .set("display", "inline-block").set("padding", "3px 12px")
                .set("border-radius", "999px")
                .set("background", s.getColor()).set("color", "white")
                .set("font-size", "12px").set("font-weight", "800")
                .set("letter-spacing", "0.04em");
        return badge;
    }

    // ── Detail pane ──────────────────────────────────────────────────────

    private Div buildDetailCard() {
        Div card = whiteCard();
        card.getStyle().set("position", "sticky").set("top", "24px");
        card.add(sectionHeading("MESSAGE DETAIL",
                "Inspect the email, manage delivery state, or open the printable receipt."));
        card.add(detailHolder);
        return card;
    }

    private void renderDetail() {
        detailHolder.removeAll();
        if (selectedEntry == null) {
            Span hint = new Span("Select a message on the left to inspect its contents.");
            hint.getStyle()
                    .set("display", "block").set("color", LIGHT_MUTED)
                    .set("font-style", "italic").set("padding", "18px 0");
            detailHolder.add(hint);
            return;
        }
        detailHolder.add(buildDetailHeader());
        detailHolder.add(buildDetailMetaGrid());
        detailHolder.add(buildDetailActions());
        detailHolder.add(buildBodyPreview());
        detailHolder.add(buildStatusControls());
    }

    private Div buildDetailHeader() {
        Div block = new Div();
        block.getStyle().set("margin-bottom", "14px");

        Span subject = new Span(selectedEntry.getSubject() == null
                ? "Cancellation Confirmation" : selectedEntry.getSubject());
        subject.getStyle()
                .set("display", "block").set("font-size", "15px")
                .set("font-weight", "900").set("color", LIGHT_TEXT)
                .set("margin-bottom", "4px");

        Span to = new Span("To: " + selectedEntry.getCustomerEmail()
                + "  ·  " + selectedEntry.getCustomerName());
        to.getStyle()
                .set("display", "block").set("font-size", "12px").set("color", LIGHT_MUTED);

        block.add(subject, to);
        return block;
    }

    private Div buildDetailMetaGrid() {
        Div meta = new Div();
        meta.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(2, minmax(0,1fr))")
                .set("gap", "10px").set("margin-bottom", "14px");
        meta.add(metaCell("Source",   selectedEntry.getSource().getDisplayName(), BLUE));
        meta.add(metaCell("Status",   selectedEntry.getStatus().getDisplayName(),
                selectedEntry.getStatus().getColor()));
        meta.add(metaCell("Booking",  selectedEntry.getBookingReference(), VIP_PURPLE));
        meta.add(metaCell("Refund",   formatMoney(selectedEntry.getRefundAmount()), SUCCESS));
        meta.add(metaCell("Template", humaniseTemplate(selectedEntry.getTemplateKey()), AMBER));
        meta.add(metaCell("Sent",
                selectedEntry.getCreatedAt() == null ? "—"
                        : selectedEntry.getCreatedAt().format(STAMP_FMT), LIGHT_MUTED));
        return meta;
    }

    private Div metaCell(String label, String value, String accent) {
        Div cell = new Div();
        cell.getStyle()
                .set("background", "#f8fafc").set("border-left", "4px solid " + accent)
                .set("border-radius", "8px").set("padding", "8px 12px");
        Span lab = new Span(label);
        lab.getStyle()
                .set("display", "block").set("font-size", "10.5px")
                .set("font-weight", "800").set("letter-spacing", "0.08em")
                .set("color", LIGHT_MUTED).set("margin-bottom", "2px")
                .set("text-transform", "uppercase");
        Span val = new Span(value == null || value.isBlank() ? "—" : value);
        val.getStyle()
                .set("font-size", "13px").set("font-weight", "800").set("color", LIGHT_TEXT);
        cell.add(lab, val);
        return cell;
    }

    private Div buildDetailActions() {
        Div actions = new Div();
        actions.getStyle()
                .set("display", "flex").set("gap", "10px").set("flex-wrap", "wrap")
                .set("margin-bottom", "14px");

        Button openEmail = new Button("Open Email Preview", new Icon(VaadinIcon.ENVELOPE));
        openEmail.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        openEmail.getStyle().set("font-weight", "800").set("background", BLUE);
        openEmail.addClickListener(e -> {
            if (selectedEntry.getEmail() == null) {
                Notification.show("No email body recorded for this entry.",
                                2500, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
                return;
            }
            new EmailPreviewDialog(selectedEntry.getEmail()).open();
        });

        Button openReceipt = new Button("Open Receipt", new Icon(VaadinIcon.FILE_TEXT_O));
        openReceipt.getStyle()
                .set("font-weight", "800").set("color", VIP_PURPLE)
                .set("background", "white").set("border", "1.5px solid " + VIP_PURPLE);
        openReceipt.addClickListener(e -> {
            if (selectedEntry.getReceipt() == null) {
                Notification.show("No receipt has been generated for this message yet.",
                                3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
                return;
            }
            new ReceiptPreviewDialog(selectedEntry.getReceipt()).open();
        });

        actions.add(openEmail, openReceipt);
        return actions;
    }

    private Div buildBodyPreview() {
        Div body = new Div();
        body.getStyle()
                .set("background", "white").set("border", "1px solid " + CARD_BORDER)
                .set("border-radius", "8px").set("padding", "14px 16px")
                .set("font-family", "monospace").set("font-size", "12px")
                .set("line-height", "1.65").set("white-space", "pre-wrap")
                .set("color", LIGHT_TEXT).set("max-height", "260px")
                .set("overflow", "auto").set("margin-bottom", "14px");
        StringBuilder sb = new StringBuilder();
        if (selectedEntry.getEmail() != null
                && selectedEntry.getEmail().getBodyLines() != null) {
            for (String line : selectedEntry.getEmail().getBodyLines()) {
                sb.append(line).append('\n');
            }
        }
        body.setText(sb.length() == 0 ? "(email body unavailable)" : sb.toString());
        return body;
    }

    private Div buildStatusControls() {
        Div wrap = new Div();
        wrap.getStyle()
                .set("display", "flex").set("flex-direction", "column").set("gap", "8px")
                .set("padding-top", "12px").set("border-top", "1px dashed " + CARD_BORDER);
        Span title = new Span("Update delivery status");
        title.getStyle()
                .set("font-size", "11px").set("font-weight", "800")
                .set("letter-spacing", "0.08em").set("color", LIGHT_MUTED)
                .set("text-transform", "uppercase");
        wrap.add(title);

        Div row = new Div();
        row.getStyle().set("display", "flex").set("gap", "8px").set("flex-wrap", "wrap");
        row.add(statusButton("Mark Sent",    EmailLogEntry.Status.SENT,    SUCCESS));
        row.add(statusButton("Mark Pending", EmailLogEntry.Status.PENDING, AMBER));
        row.add(statusButton("Mark Failed",  EmailLogEntry.Status.FAILED,  DANGER));
        wrap.add(row);

        if (selectedEntry.getStatusNote() != null
                && !selectedEntry.getStatusNote().isBlank()) {
            Span note = new Span("Last note: " + selectedEntry.getStatusNote());
            note.getStyle()
                    .set("font-size", "12px").set("color", LIGHT_MUTED)
                    .set("font-style", "italic");
            wrap.add(note);
        }
        return wrap;
    }

    private Button statusButton(String label, EmailLogEntry.Status next, String color) {
        Button b = new Button(label);
        b.getStyle()
                .set("font-weight", "800").set("color", "white")
                .set("background", color).set("border-radius", "8px")
                .set("padding", "0 14px").set("height", "34px");
        b.addClickListener(e -> {
            emailLogService.markStatus(selectedEntry.getId(), next,
                    "Set to " + next.getDisplayName() + " by "
                            + currentOperator() + " from Email Management.");
            Notification.show("Delivery status updated to " + next.getDisplayName(),
                            2500, Notification.Position.TOP_END)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refresh();
            // Re-select the same row so the detail pane stays in context.
            emailLogService.findById(selectedEntry.getId()).ifPresent(updated -> {
                selectedEntry = updated;
                grid.select(updated);
                renderDetail();
            });
        });
        return b;
    }

    // ── Template management ──────────────────────────────────────────────

    private Div buildTemplateSection() {
        Div wrap = new Div();
        wrap.getStyle().set("margin-top", "32px");
        Div card = whiteCard();
        card.add(sectionHeading("EMAIL TEMPLATES",
                "Pre-defined message templates that drive the email body for each "
                        + "cancellation scenario. Edit-in-place will be enabled in a future release."));

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(280px, 1fr))")
                .set("gap", "14px");

        grid.add(templateCard("Standard Refund",
                EmailLogService.TEMPLATE_SINGLE_STANDARD, BLUE,
                "Sent automatically for self-service cancellations under the "
                        + "Standard Policy. Neutral, business-formal tone with a full "
                        + "refund breakdown and a 14-day correction window."));
        grid.add(templateCard("VIP Cancellation",
                EmailLogService.TEMPLATE_SINGLE_VIP, VIP_PURPLE,
                "Reserved for HCBS Premium members. Highlights expedited handling, "
                        + "complimentary rewards and concierge follow-up."));
        grid.add(templateCard("Emergency (Single)",
                EmailLogService.TEMPLATE_SINGLE_EMERGENCY, DANGER,
                "Used when a single booking has to be cancelled by staff under "
                        + "the Emergency Policy. Apologetic tone with goodwill vouchers."));
        grid.add(templateCard("Emergency (Batch)",
                EmailLogService.TEMPLATE_BATCH_EMERGENCY, AMBER,
                "Mass-cancellation template used by the Batch Cancellation Dashboard "
                        + "for cinema-wide incidents. Uniform tone, identical to every recipient."));
        card.add(grid);
        wrap.add(card);
        return wrap;
    }

    private Div templateCard(String name, String key, String accent, String description) {
        Div card = new Div();
        card.getStyle()
                .set("background", "white").set("color", LIGHT_TEXT)
                .set("border", "1px solid " + CARD_BORDER)
                .set("border-left", "5px solid " + accent)
                .set("border-radius", "10px").set("padding", "16px 18px");
        Span title = new Span(name);
        title.getStyle()
                .set("display", "block").set("font-weight", "900")
                .set("font-size", "14.5px").set("margin-bottom", "4px");
        Span identifier = new Span(key);
        identifier.getStyle()
                .set("display", "block").set("font-size", "11px")
                .set("font-family", "monospace").set("color", LIGHT_MUTED)
                .set("margin-bottom", "8px").set("letter-spacing", "0.04em");
        Paragraph p = new Paragraph(description);
        p.getStyle()
                .set("margin", "0").set("font-size", "12.5px")
                .set("color", LIGHT_TEXT).set("line-height", "1.55");

        long usage = emailLogService.findAll().stream()
                .filter(e -> key.equals(e.getTemplateKey())).count();
        Span chip = new Span(usage + " message(s) generated");
        chip.getStyle()
                .set("display", "inline-block").set("margin-top", "10px")
                .set("padding", "3px 10px").set("background", "rgba(15,23,42,0.06)")
                .set("color", LIGHT_TEXT).set("font-size", "11.5px")
                .set("font-weight", "800").set("border-radius", "999px");

        card.add(title, identifier, p, chip);
        return card;
    }

    // ── Listeners + refresh ──────────────────────────────────────────────

    private void wireListeners() {
        sourceFilter.addValueChangeListener(e -> applyFilters());
        statusFilter.addValueChangeListener(e -> applyFilters());
        keywordFilter.addValueChangeListener(e -> applyFilters());
        refreshButton.addClickListener(e -> {
            refresh();
            Notification.show("Email log refreshed.", 1800, Notification.Position.TOP_END)
                    .addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        });
        clearButton.addClickListener(e -> {
            sourceFilter.clear();
            statusFilter.clear();
            keywordFilter.clear();
            refresh();
        });
    }

    private void refresh() {
        rows.clear();
        rows.addAll(emailLogService.filter(
                sourceFilter.getValue(),
                statusFilter.getValue(),
                keywordFilter.getValue()));
        // Newest first; the service already stores in insertion order so this
        // just guards against future ordering changes.
        rows.sort(Comparator.comparing(EmailLogEntry::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        dataProvider.refreshAll();
        updateMetrics();
        if (selectedEntry != null) {
            emailLogService.findById(selectedEntry.getId()).ifPresent(updated -> {
                selectedEntry = updated;
                renderDetail();
            });
        } else {
            renderDetail();
        }
    }

    private void applyFilters() {
        refresh();
    }

    private void updateMetrics() {
        totalChip.setText(String.valueOf(emailLogService.count()));
        sentChip.setText(String.valueOf(emailLogService.countByStatus(EmailLogEntry.Status.SENT)));
        pendingChip.setText(String.valueOf(
                emailLogService.countByStatus(EmailLogEntry.Status.PENDING)));
        failedChip.setText(String.valueOf(
                emailLogService.countByStatus(EmailLogEntry.Status.FAILED)));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String currentOperator() {
        if (loginService.getCurrentUser() == null) return "system";
        String username = loginService.getCurrentUser().getUsername();
        return username == null ? "system" : username;
    }

    private String humaniseTemplate(String key) {
        if (key == null) return "—";
        if (key.equals(EmailLogService.TEMPLATE_SINGLE_STANDARD))  return "Standard Refund";
        if (key.equals(EmailLogService.TEMPLATE_SINGLE_VIP))       return "VIP Cancellation";
        if (key.equals(EmailLogService.TEMPLATE_SINGLE_EMERGENCY)) return "Emergency (Single)";
        if (key.equals(EmailLogService.TEMPLATE_BATCH_EMERGENCY))  return "Emergency (Batch)";
        return key;
    }

    private Div whiteCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", "white").set("color", LIGHT_TEXT)
                .set("padding", "22px 24px").set("border-radius", "10px")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.30)")
                .set("border", "1px solid " + CARD_BORDER);
        return card;
    }

    private Div sectionHeading(String headline, String subtitle) {
        Div header = new Div();
        header.getStyle().set("margin-bottom", "16px");
        H3 h = new H3(headline);
        h.getStyle()
                .set("margin", "0").set("color", BLUE).set("font-weight", "900")
                .set("letter-spacing", "0.06em");
        Span s = new Span(subtitle);
        s.getStyle()
                .set("display", "block").set("font-size", "12px")
                .set("color", LIGHT_MUTED).set("font-style", "italic")
                .set("margin-top", "4px");
        header.add(h, s);
        return header;
    }

    private String formatMoney(BigDecimal amount) {
        return MONEY_FMT.format(amount == null ? BigDecimal.ZERO : amount);
    }
}
