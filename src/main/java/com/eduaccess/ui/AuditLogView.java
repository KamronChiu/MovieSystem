package com.eduaccess.ui;

import com.eduaccess.domain.AuditAction;
import com.eduaccess.domain.AuditLog;
import com.eduaccess.service.AuditLogService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Route(value = "admin/audit", layout = MainLayout.class)
@PageTitle("HCBS — Audit Log")
public class AuditLogView extends Div implements BeforeEnterObserver {

    private static final String DARK_BG = "#020b1d";
    private static final String PANEL = "#071428";
    private static final String BORDER = "rgba(255,255,255,0.13)";
    private static final String BLUE = "#0072ce";

    private final LoginService loginService;
    private final AuditLogService auditLogService;

    private final DatePicker startDate = new DatePicker("Start date");
    private final DatePicker endDate = new DatePicker("End date");
    private final ComboBox<AuditAction> actionFilter = new ComboBox<>("Action");
    private final Grid<AuditLog> grid = new Grid<>(AuditLog.class, false);
    private final Div exportHolder = new Div();

    private List<AuditLog> currentLogs = List.of();

    public AuditLogView(LoginService loginService, AuditLogService auditLogService) {
        this.loginService = loginService;
        this.auditLogService = auditLogService;

        setWidthFull();
        getStyle()
                .set("background", DARK_BG)
                .set("min-height", "100vh")
                .set("color", "white");

        Div page = new Div();
        page.getStyle()
                .set("max-width", "1420px")
                .set("margin", "0 auto")
                .set("padding", "44px 42px 80px")
                .set("box-sizing", "border-box");

        configureFilters();
        configureGrid();

        page.add(buildHeader(), buildFilterBar(), buildGridPanel());
        add(page);

        refreshLogs();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkAdminAccess(event, loginService);
    }

    private Div buildHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "end")
                .set("gap", "20px")
                .set("margin-bottom", "26px");

        Div text = new Div();
        H1 title = new H1("AUDIT LOG");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "38px")
                .set("letter-spacing", "0.08em")
                .set("font-weight", "950");

        Paragraph subtitle = new Paragraph("Track booking, cancellation, cinema management, scheduling, food order and dashboard feedback operations.");
        subtitle.getStyle()
                .set("margin", "10px 0 0")
                .set("color", "#a8b3c7")
                .set("font-size", "16px");

        text.add(title, subtitle);

        Span count = new Span();
        count.getElement().setAttribute("id", "audit-count-label");
        count.getStyle()
                .set("padding", "12px 18px")
                .set("border", "1px solid " + BORDER)
                .set("border-radius", "999px")
                .set("background", "rgba(255,255,255,0.04)")
                .set("font-weight", "900");

        header.add(text, count);
        return header;
    }

    private void configureFilters() {
        startDate.setValue(LocalDate.now().minusDays(30));
        endDate.setValue(LocalDate.now());

        actionFilter.setItems(AuditAction.values());
        actionFilter.setItemLabelGenerator(AuditAction::getLabel);
        actionFilter.setPlaceholder("All actions");
        actionFilter.setClearButtonVisible(true);

        styleInput(startDate);
        styleInput(endDate);
        styleInput(actionFilter);
    }

    private Div buildFilterBar() {
        Div bar = new Div();
        bar.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "180px 180px 260px auto auto")
                .set("gap", "14px")
                .set("align-items", "end")
                .set("background", PANEL)
                .set("border", "1px solid " + BORDER)
                .set("border-radius", "22px")
                .set("padding", "18px")
                .set("margin-bottom", "24px");

        Button apply = new Button("Apply", event -> refreshLogs());
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        stylePrimary(apply);

        Button recent = new Button("Last 30 days", event -> {
            startDate.setValue(LocalDate.now().minusDays(30));
            endDate.setValue(LocalDate.now());
            actionFilter.clear();
            refreshLogs();
        });
        styleSecondary(recent);

        exportHolder.getStyle().set("display", "flex").set("align-items", "end");

        bar.add(startDate, endDate, actionFilter, apply, recent, exportHolder);
        return bar;
    }

    private Div buildGridPanel() {
        Div panel = new Div(grid);
        panel.getStyle()
                .set("background", PANEL)
                .set("border", "1px solid " + BORDER)
                .set("border-radius", "22px")
                .set("padding", "18px")
                .set("box-shadow", "0 22px 60px rgba(0,0,0,0.22)");
        return panel;
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addColumn(log -> log.getCreatedAt() == null ? "-" : log.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .setHeader("Time")
                .setAutoWidth(true);
        grid.addColumn(log -> log.getAction() == null ? "-" : log.getAction().getLabel())
                .setHeader("Action")
                .setAutoWidth(true);
        grid.addColumn(AuditLog::getActorUsername)
                .setHeader("User")
                .setAutoWidth(true);
        grid.addColumn(AuditLog::getActorRole)
                .setHeader("Role")
                .setAutoWidth(true);
        grid.addColumn(AuditLog::getReference)
                .setHeader("Reference")
                .setAutoWidth(true);
        grid.addColumn(AuditLog::getFilmTitle)
                .setHeader("Film")
                .setAutoWidth(true);
        grid.addColumn(AuditLog::getCinemaName)
                .setHeader("Cinema")
                .setAutoWidth(true);
        grid.addColumn(log -> log.getAmount() == null ? "-" : "£" + log.getAmount())
                .setHeader("Amount")
                .setAutoWidth(true);
        grid.addColumn(AuditLog::getSummary)
                .setHeader("Summary")
                .setFlexGrow(1);
        grid.addColumn(AuditLog::getDetails)
                .setHeader("Details")
                .setFlexGrow(2);

        grid.getStyle()
                .set("--vaadin-grid-cell-background", PANEL)
                .set("--vaadin-grid-cell-color", "#e5e7eb")
                .set("--vaadin-grid-header-cell-background", "#0f1d33")
                .set("--vaadin-grid-header-cell-color", "#c7d2fe")
                .set("border-radius", "16px")
                .set("overflow", "hidden");
    }

    private void refreshLogs() {
        if (startDate.getValue() != null && endDate.getValue() != null && startDate.getValue().isAfter(endDate.getValue())) {
            Notification.show("Start date cannot be after end date.");
            return;
        }

        List<AuditLog> logs = auditLogService.findLogsBetween(startDate.getValue(), endDate.getValue());
        AuditAction selectedAction = actionFilter.getValue();

        if (selectedAction != null) {
            logs = logs.stream()
                    .filter(log -> Objects.equals(log.getAction(), selectedAction))
                    .toList();
        }

        currentLogs = logs;
        grid.setItems(currentLogs);
        getElement().executeJs("const el = document.getElementById('audit-count-label'); if (el) el.textContent = $0;", currentLogs.size() + " audit events");
        updateExportLink();
    }

    private void updateExportLink() {
        exportHolder.removeAll();

        StreamResource resource = new StreamResource(
                "audit-log-export.csv",
                () -> new ByteArrayInputStream(buildCsv().getBytes(StandardCharsets.UTF_8))
        );
        resource.setContentType("text/csv");

        Anchor export = new Anchor(resource, "Export CSV");
        export.getElement().setAttribute("download", true);
        export.getStyle()
                .set("height", "44px")
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("padding", "0 18px")
                .set("border-radius", "999px")
                .set("background", BLUE)
                .set("color", "white")
                .set("font-weight", "900")
                .set("text-decoration", "none");
        exportHolder.add(export);
    }

    private String buildCsv() {
        StringBuilder csv = new StringBuilder("Time,Action,User,Role,Reference,Film,Cinema,Amount,Summary,Details\n");
        for (AuditLog log : currentLogs) {
            csv.append(escape(log.getCreatedAt() == null ? "" : log.getCreatedAt().toString())).append(',')
                    .append(escape(log.getAction() == null ? "" : log.getAction().getLabel())).append(',')
                    .append(escape(log.getActorUsername())).append(',')
                    .append(escape(log.getActorRole())).append(',')
                    .append(escape(log.getReference())).append(',')
                    .append(escape(log.getFilmTitle())).append(',')
                    .append(escape(log.getCinemaName())).append(',')
                    .append(escape(log.getAmount() == null ? "" : log.getAmount().toString())).append(',')
                    .append(escape(log.getSummary())).append(',')
                    .append(escape(log.getDetails())).append('\n');
        }
        return csv.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\n", " ").replace("\r", " ");
        return "\"" + cleaned.replace("\"", "\"\"") + "\"";
    }

    private void styleInput(com.vaadin.flow.component.Component component) {
        component.getElement().getStyle()
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.07)")
                .set("--vaadin-input-field-value-color", "white")
                .set("--vaadin-input-field-label-color", "#a8b3c7")
                .set("--vaadin-input-field-placeholder-color", "#64748b")
                .set("--vaadin-input-field-border-color", BORDER);
    }

    private void stylePrimary(Button button) {
        button.getStyle()
                .set("height", "44px")
                .set("background", BLUE)
                .set("color", "white")
                .set("font-weight", "900")
                .set("border-radius", "999px")
                .set("padding", "0 22px");
    }

    private void styleSecondary(Button button) {
        button.getStyle()
                .set("height", "44px")
                .set("background", "rgba(255,255,255,0.05)")
                .set("color", "white")
                .set("font-weight", "850")
                .set("border", "1px solid " + BORDER)
                .set("border-radius", "999px")
                .set("padding", "0 20px");
    }
}
