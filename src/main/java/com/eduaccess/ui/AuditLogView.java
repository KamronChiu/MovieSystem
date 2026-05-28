package com.eduaccess.ui;

import com.eduaccess.domain.AuditLog;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.service.AuditService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Audit Log Grid page.
 * <p>
 * Lists every audit entry written by {@link AuditService}, with filters for
 * action keyword, booking reference, and operator. Style mirrors the
 * {@link CancellationView} grid (dark hero + white master card) so the
 * page feels native to the rest of the system.
 */
@Route(value = "audit-logs", layout = MainLayout.class)
@PageTitle("HCBS — Audit Log")
public class AuditLogView extends Div implements BeforeEnterObserver {

    private static final String DARK_BG = "#020b1d";
    private static final String BLUE = "#0072ce";
    private static final String LIGHT_TEXT = "#142033";
    private static final String LIGHT_MUTED = "#64748b";
    private static final String CARD_BORDER = "#d8e2ef";

    private static final DateTimeFormatter STAMP_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.UK);

    private static final String ALL_ACTIONS = "All actions";

    private final AuditService auditService;
    private final LoginService loginService;

    private final TextField targetField = new TextField("Booking Reference");
    private final TextField operatorField = new TextField("Operator");
    private final ComboBox<String> actionFilter = new ComboBox<>("Action");
    private final Button refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
    private final Button clearButton = new Button("Clear");

    private final Grid<AuditLog> grid = new Grid<>(AuditLog.class, false);
    private final List<AuditLog> allEntries = new ArrayList<>();
    private final ListDataProvider<AuditLog> dataProvider =
            new ListDataProvider<>(allEntries);

    private final Span totalLabel = new Span();

    public AuditLogView(AuditService auditService, LoginService loginService) {
        this.auditService = auditService;
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

        container.add(buildHeader());
        container.add(buildFilterBar());
        container.add(buildGridCard());

        add(container);

        refreshEntries();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Audit log is reused for the cancellation team — gate behind
        // cancellation access so non-staff cannot see operator details.
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

        Icon titleIcon = new Icon(VaadinIcon.CLIPBOARD_TEXT);
        titleIcon.setSize("28px");
        titleIcon.getStyle().set("color", "#9ec5ff");

        H2 title = new H2("Audit Log");
        title.getStyle()
                .set("margin", "0")
                .set("font-weight", "950")
                .set("letter-spacing", "0.04em");

        titleRow.add(titleIcon, title);

        Span subtitle = new Span(
                "Immutable trail of every cancellation action — operator, status change, IP, timestamp.");
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

    // ── Filter bar (white card, mirrors CancellationView search bar) ──────

    private Div buildFilterBar() {
        Div bar = new Div();
        bar.getStyle()
                .set("background", "white")
                .set("color", LIGHT_TEXT)
                .set("padding", "20px 24px")
                .set("margin-bottom", "24px")
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr 1fr 140px 110px")
                .set("gap", "16px")
                .set("align-items", "end")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.3)")
                .set("border-radius", "8px");

        actionFilter.setItems(buildActionItems());
        actionFilter.setValue(ALL_ACTIONS);
        actionFilter.setWidthFull();
        actionFilter.setClearButtonVisible(false);
        actionFilter.addValueChangeListener(e -> applyFilters());

        targetField.setPlaceholder("e.g. HCBS-...");
        targetField.setWidthFull();
        targetField.setPrefixComponent(new Icon(VaadinIcon.HASH));
        targetField.setClearButtonVisible(true);
        targetField.setValueChangeMode(ValueChangeMode.LAZY);
        targetField.addValueChangeListener(e -> applyFilters());

        operatorField.setPlaceholder("e.g. admin");
        operatorField.setWidthFull();
        operatorField.setPrefixComponent(new Icon(VaadinIcon.USER));
        operatorField.setClearButtonVisible(true);
        operatorField.setValueChangeMode(ValueChangeMode.LAZY);
        operatorField.addValueChangeListener(e -> applyFilters());

        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refreshButton.getStyle()
                .set("background", BLUE)
                .set("color", "white")
                .set("height", "44px")
                .set("font-weight", "800");
        refreshButton.addClickListener(e -> {
            refreshEntries();
            Notification.show("Audit log refreshed",
                            1500, Notification.Position.TOP_END)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        clearButton.getStyle().set("height", "44px");
        clearButton.addClickListener(e -> {
            actionFilter.setValue(ALL_ACTIONS);
            targetField.clear();
            operatorField.clear();
            applyFilters();
        });

        bar.add(actionFilter, targetField, operatorField, refreshButton, clearButton);
        return bar;
    }

    private List<String> buildActionItems() {
        List<String> items = new ArrayList<>();
        items.add(ALL_ACTIONS);
        items.add(AuditService.ACTION_CANCEL_BOOKING);
        items.add(AuditService.ACTION_ADVANCE_STATUS);
        items.add(AuditService.ACTION_UPDATE_REASON);
        items.add(AuditService.ACTION_UPDATE_VIP);
        return items;
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
        grid.addColumn(log -> log.getTimestamp() == null
                        ? "—" : log.getTimestamp().format(STAMP_FMT))
                .setHeader("Timestamp")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addComponentColumn(this::renderActionBadge)
                .setHeader("Action")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(AuditLog::getOperator)
                .setHeader("Operator")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(log -> log.getTargetReference() == null
                        ? "—" : log.getTargetReference())
                .setHeader("Booking Ref")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addComponentColumn(this::renderTransition)
                .setHeader("Status Change")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(log -> log.getIpAddress() == null
                        ? "—" : log.getIpAddress())
                .setHeader("IP Address")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(log -> log.getDetails() == null ? "" : log.getDetails())
                .setHeader("Details")
                .setFlexGrow(1);

        grid.setDataProvider(dataProvider);
        grid.setHeight("680px");
        grid.setMultiSort(true);
    }

    private Span renderActionBadge(AuditLog log) {
        Span badge = new Span(prettyAction(log.getAction()));
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "4px 10px")
                .set("border-radius", "999px")
                .set("font-size", "12px")
                .set("font-weight", "800")
                .set("letter-spacing", "0.03em")
                .set("background", actionBackground(log.getAction()))
                .set("color", actionColor(log.getAction()));
        return badge;
    }

    private Div renderTransition(AuditLog log) {
        Div wrap = new Div();
        wrap.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "6px");

        if (log.getOldStatus() == null && log.getNewStatus() == null) {
            Span dash = new Span("—");
            dash.getStyle().set("color", LIGHT_MUTED);
            wrap.add(dash);
            return wrap;
        }

        wrap.add(statusPill(log.getOldStatus()));

        Icon arrow = new Icon(VaadinIcon.ARROW_RIGHT);
        arrow.setSize("14px");
        arrow.getStyle().set("color", LIGHT_MUTED);
        wrap.add(arrow);

        wrap.add(statusPill(log.getNewStatus()));
        return wrap;
    }

    private Span statusPill(BookingStatus status) {
        if (status == null) {
            Span empty = new Span("—");
            empty.getStyle().set("color", LIGHT_MUTED);
            return empty;
        }
        Span pill = new Span(status.getDisplayName());
        pill.getStyle()
                .set("display", "inline-block")
                .set("padding", "3px 10px")
                .set("border-radius", "999px")
                .set("font-size", "12px")
                .set("font-weight", "800")
                .set("background", status.getBadgeBackground())
                .set("color", status.getBadgeTextColor());
        return pill;
    }

    private String prettyAction(String action) {
        if (action == null) {
            return "—";
        }
        return action.replace("_", " ");
    }

    private String actionBackground(String action) {
        if (action == null) {
            return "#e2e8f0";
        }
        return switch (action) {
            case AuditService.ACTION_CANCEL_BOOKING -> "#fee2e2";
            case AuditService.ACTION_ADVANCE_STATUS -> "#dbeafe";
            case AuditService.ACTION_UPDATE_REASON  -> "#fef3c7";
            case AuditService.ACTION_UPDATE_VIP     -> "#ede9fe";
            default -> "#e2e8f0";
        };
    }

    private String actionColor(String action) {
        if (action == null) {
            return "#475569";
        }
        return switch (action) {
            case AuditService.ACTION_CANCEL_BOOKING -> "#b91c1c";
            case AuditService.ACTION_ADVANCE_STATUS -> "#1d4ed8";
            case AuditService.ACTION_UPDATE_REASON  -> "#92400e";
            case AuditService.ACTION_UPDATE_VIP     -> "#6d28d9";
            default -> "#475569";
        };
    }

    // ── Data + filters ────────────────────────────────────────────────────

    private void refreshEntries() {
        allEntries.clear();
        allEntries.addAll(auditService.findAll());
        dataProvider.refreshAll();
        applyFilters();
    }

    private void applyFilters() {
        String selectedAction = actionFilter.getValue();
        String target = targetField.getValue() == null ? "" : targetField.getValue().trim().toUpperCase();
        String operator = operatorField.getValue() == null ? "" : operatorField.getValue().trim().toLowerCase();

        dataProvider.setFilter(log -> {
            if (selectedAction != null && !selectedAction.isBlank()
                    && !ALL_ACTIONS.equals(selectedAction)
                    && !selectedAction.equals(log.getAction())) {
                return false;
            }
            if (!target.isEmpty()) {
                String ref = log.getTargetReference();
                if (ref == null || !ref.toUpperCase().contains(target)) {
                    return false;
                }
            }
            if (!operator.isEmpty()) {
                String op = log.getOperator();
                if (op == null || !op.toLowerCase().contains(operator)) {
                    return false;
                }
            }
            return true;
        });

        int visible = dataProvider.size(new com.vaadin.flow.data.provider.Query<>());
        totalLabel.setText(visible + " entries (of " + allEntries.size() + ")");
    }
}
