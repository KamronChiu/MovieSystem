package com.eduaccess.ui;

import com.eduaccess.domain.ManagerFeedback;
import com.eduaccess.service.LoginService;
import com.eduaccess.service.ManagerDashboardService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Route(value = "manager/dashboard", layout = MainLayout.class)
@PageTitle("HCBS — Manager Dashboard")
public class ManagerDashboardView extends Div implements BeforeEnterObserver {

    private static final String DARK_BG = "#020b1d";
    private static final String PANEL = "#071428";
    private static final String PANEL_SOFT = "#0f1d33";
    private static final String BORDER = "rgba(255,255,255,0.13)";
    private static final String BLUE = "#0072ce";
    private static final String CYAN = "#38bdf8";
    private static final String ORANGE = "#f59e0b";
    private static final String GREEN = "#10b981";
    private static final String RED = "#ef4444";
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.UK);

    private final LoginService loginService;
    private final ManagerDashboardService dashboardService;

    private final DatePicker startDate = new DatePicker("Start date");
    private final DatePicker endDate = new DatePicker("End date");
    private final Div exportHolder = new Div();
    private final Div content = new Div();
    private final TextField feedbackTitle = new TextField("Feedback title");
    private final TextArea feedbackComment = new TextArea("Manager feedback / interpretation");

    private ManagerDashboardService.DashboardData dashboardData;

    public ManagerDashboardView(LoginService loginService, ManagerDashboardService dashboardService) {
        this.loginService = loginService;
        this.dashboardService = dashboardService;

        setWidthFull();
        getStyle()
                .set("background", DARK_BG)
                .set("min-height", "100vh")
                .set("color", "white");

        Div page = new Div();
        page.getStyle()
                .set("max-width", "1460px")
                .set("margin", "0 auto")
                .set("padding", "44px 42px 90px")
                .set("box-sizing", "border-box");

        configureFields();
        page.add(buildHeader(), buildControls(), content);
        add(page);

        refreshDashboard();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkManagerAccess(event, loginService);
    }

    private void configureFields() {
        startDate.setValue(LocalDate.now().minusDays(30));
        endDate.setValue(LocalDate.now());
        styleInput(startDate);
        styleInput(endDate);

        feedbackTitle.setPlaceholder("e.g. Weekly sales review");
        feedbackTitle.setWidthFull();
        feedbackComment.setPlaceholder("Write your interpretation of sales performance, customer demand, or suggested schedule changes.");
        feedbackComment.setWidthFull();
        feedbackComment.setMinHeight("150px");
        styleInput(feedbackTitle);
        styleInput(feedbackComment);
    }

    private Div buildHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "end")
                .set("gap", "24px")
                .set("margin-bottom", "26px");

        Div text = new Div();
        H1 title = new H1("MANAGER INSIGHTS");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "40px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.08em");

        Paragraph subtitle = new Paragraph("Visualise ticket sales, food revenue, cinema performance and manager feedback in one place.");
        subtitle.getStyle()
                .set("margin", "10px 0 0")
                .set("color", "#a8b3c7")
                .set("font-size", "16px");

        text.add(title, subtitle);
        header.add(text, exportHolder);
        return header;
    }

    private Div buildControls() {
        Div controls = new Div();
        controls.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "180px 180px auto auto")
                .set("gap", "14px")
                .set("align-items", "end")
                .set("background", PANEL)
                .set("border", "1px solid " + BORDER)
                .set("border-radius", "22px")
                .set("padding", "18px")
                .set("margin-bottom", "24px");

        Button apply = new Button("Apply", event -> refreshDashboard());
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        stylePrimary(apply);

        Button month = new Button("Last 30 days", event -> {
            startDate.setValue(LocalDate.now().minusDays(30));
            endDate.setValue(LocalDate.now());
            refreshDashboard();
        });
        styleSecondary(month);

        controls.add(startDate, endDate, apply, month);
        return controls;
    }

    private void refreshDashboard() {
        if (startDate.getValue() != null && endDate.getValue() != null && startDate.getValue().isAfter(endDate.getValue())) {
            Notification.show("Start date cannot be after end date.");
            return;
        }

        dashboardData = dashboardService.buildDashboard(startDate.getValue(), endDate.getValue());
        content.removeAll();
        content.add(
                buildSummaryCards(dashboardData.summary()),
                buildChartsGrid(),
                buildFeedbackPanel()
        );
        updateExportLink();
    }

    private Div buildSummaryCards(ManagerDashboardService.Summary summary) {
        Div cards = new Div();
        cards.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(6, minmax(150px, 1fr))")
                .set("gap", "16px")
                .set("margin-bottom", "24px");

        cards.add(
                statCard("Bookings", String.valueOf(summary.confirmedBookings()), "Confirmed bookings", CYAN),
                statCard("Tickets", String.valueOf(summary.ticketsSold()), "Seats sold", BLUE),
                statCard("Cancelled", String.valueOf(summary.cancelledBookings()), "Cancelled bookings", RED),
                statCard("Ticket revenue", money(summary.ticketRevenue()), "Confirmed ticket sales", GREEN),
                statCard("Food revenue", money(summary.foodRevenue()), "Concessions", ORANGE),
                statCard("Grand total", money(summary.grandRevenue()), "Tickets + food", "#8b5cf6")
        );

        return cards;
    }

    private Div statCard(String label, String value, String hint, String accent) {
        Div card = new Div();
        card.getStyle()
                .set("background", PANEL)
                .set("border", "1px solid " + BORDER)
                .set("border-left", "4px solid " + accent)
                .set("border-radius", "20px")
                .set("padding", "18px")
                .set("box-shadow", "0 18px 45px rgba(0,0,0,0.20)");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("display", "block")
                .set("color", "#a8b3c7")
                .set("font-size", "13px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.08em")
                .set("text-transform", "uppercase");

        Span valueSpan = new Span(value);
        valueSpan.getStyle()
                .set("display", "block")
                .set("font-size", "28px")
                .set("font-weight", "950")
                .set("margin", "10px 0 6px")
                .set("color", "white");

        Span hintSpan = new Span(hint);
        hintSpan.getStyle()
                .set("display", "block")
                .set("color", "#64748b")
                .set("font-size", "13px")
                .set("font-weight", "750");

        card.add(labelSpan, valueSpan, hintSpan);
        return card;
    }

    private Div buildChartsGrid() {
        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1.25fr 0.75fr")
                .set("gap", "22px")
                .set("margin-bottom", "24px");

        grid.add(
                panel("Revenue trend", buildLineChart(dashboardData.dailyRevenue())),
                panel("Film revenue share", buildPieChart(dashboardData.filmSales())),
                panel("Cinema performance", buildCinemaBars(dashboardData.cinemaRevenue())),
                panel("Booking status", buildStatusBars(dashboardData.bookingStatus()))
        );

        return grid;
    }

    private Div panel(String title, Component body) {
        Div panel = new Div();
        panel.getStyle()
                .set("background", PANEL)
                .set("border", "1px solid " + BORDER)
                .set("border-radius", "22px")
                .set("padding", "22px")
                .set("box-shadow", "0 22px 60px rgba(0,0,0,0.22)")
                .set("box-sizing", "border-box");

        H2 heading = new H2(title);
        heading.getStyle()
                .set("margin", "0 0 18px")
                .set("font-size", "22px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.03em");

        panel.add(heading, body);
        return panel;
    }

    private Component buildLineChart(List<ManagerDashboardService.DailyRevenueRow> rows) {
        Div wrapper = new Div();
        wrapper.getStyle().set("width", "100%");

        if (rows == null || rows.isEmpty()) {
            wrapper.add(emptyText("No revenue data for this period."));
            return wrapper;
        }

        double max = rows.stream()
                .map(ManagerDashboardService.DailyRevenueRow::totalRevenue)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(0);

        int width = 760;
        int height = 260;
        int left = 48;
        int right = 28;
        int top = 22;
        int bottom = 52;
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;

        StringBuilder points = new StringBuilder();
        StringBuilder dots = new StringBuilder();
        StringBuilder labels = new StringBuilder();

        for (int i = 0; i < rows.size(); i++) {
            ManagerDashboardService.DailyRevenueRow row = rows.get(i);
            double x = rows.size() == 1
                    ? left + (plotWidth / 2.0)
                    : left + ((double) i / (rows.size() - 1)) * plotWidth;
            double y = max <= 0
                    ? top + plotHeight
                    : top + plotHeight - (row.totalRevenue().doubleValue() / max) * plotHeight;

            points.append(String.format(Locale.UK, "%.1f,%.1f ", x, y));
            dots.append(String.format(Locale.UK,
                    "<circle cx='%.1f' cy='%.1f' r='4' fill='%s'><title>%s: %s</title></circle>",
                    x,
                    y,
                    CYAN,
                    row.date(),
                    money(row.totalRevenue())
            ));

            if (i == 0 || i == rows.size() - 1 || i % Math.max(1, rows.size() / 6) == 0) {
                labels.append(String.format(Locale.UK,
                        "<text x='%.1f' y='%d' text-anchor='middle' fill='#94a3b8' font-size='11'>%s</text>",
                        x,
                        height - 16,
                        row.date().format(DateTimeFormatter.ofPattern("dd MMM"))
                ));
            }
        }

        String svg = """
                <svg viewBox='0 0 %d %d' width='100%%' height='280' role='img' aria-label='Revenue trend line chart'>
                    <rect x='0' y='0' width='%d' height='%d' rx='18' fill='%s'/>
                    <line x1='%d' y1='%d' x2='%d' y2='%d' stroke='rgba(255,255,255,0.18)'/>
                    <line x1='%d' y1='%d' x2='%d' y2='%d' stroke='rgba(255,255,255,0.18)'/>
                    <text x='%d' y='%d' fill='#94a3b8' font-size='12'>%s</text>
                    <polyline points='%s' fill='none' stroke='%s' stroke-width='4' stroke-linecap='round' stroke-linejoin='round'/>
                    %s
                    %s
                </svg>
                """.formatted(
                width, height,
                width, height, PANEL_SOFT,
                left, top, left, top + plotHeight,
                left, top + plotHeight, left + plotWidth, top + plotHeight,
                left + 4, top + 12, max <= 0 ? "£0" : money(BigDecimal.valueOf(max)),
                points,
                CYAN,
                dots,
                labels
        );

        wrapper.getElement().setProperty("innerHTML", svg);
        return wrapper;
    }

    private Component buildPieChart(List<ManagerDashboardService.FilmSalesRow> rows) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "180px 1fr")
                .set("gap", "18px")
                .set("align-items", "center");

        if (rows == null || rows.isEmpty()) {
            wrapper.add(emptyText("No film sales yet."));
            return wrapper;
        }

        List<ManagerDashboardService.FilmSalesRow> topRows = rows.stream().limit(5).toList();
        double total = topRows.stream()
                .map(ManagerDashboardService.FilmSalesRow::revenue)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        String[] colors = {CYAN, BLUE, ORANGE, GREEN, "#8b5cf6"};
        double cursor = 0;
        StringBuilder gradient = new StringBuilder("conic-gradient(");
        for (int i = 0; i < topRows.size(); i++) {
            double share = total <= 0 ? 100.0 / topRows.size() : (topRows.get(i).revenue().doubleValue() / total) * 100.0;
            double end = cursor + share;
            if (i > 0) {
                gradient.append(", ");
            }
            gradient.append(colors[i % colors.length]).append(' ')
                    .append(String.format(Locale.UK, "%.2f%% %.2f%%", cursor, end));
            cursor = end;
        }
        gradient.append(')');

        Div pie = new Div();
        pie.getStyle()
                .set("width", "170px")
                .set("height", "170px")
                .set("border-radius", "50%")
                .set("background", gradient.toString())
                .set("box-shadow", "0 16px 40px rgba(0,0,0,0.35)")
                .set("border", "1px solid " + BORDER);

        Div legend = new Div();
        legend.getStyle().set("display", "grid").set("gap", "10px");
        for (int i = 0; i < topRows.size(); i++) {
            ManagerDashboardService.FilmSalesRow row = topRows.get(i);
            legend.add(legendRow(colors[i % colors.length], row.filmTitle(), row.ticketsSold() + " tickets · " + money(row.revenue())));
        }

        wrapper.add(pie, legend);
        return wrapper;
    }

    private Component buildCinemaBars(List<ManagerDashboardService.CinemaRevenueRow> rows) {
        Div wrapper = new Div();
        wrapper.getStyle().set("display", "grid").set("gap", "14px");

        if (rows == null || rows.isEmpty()) {
            wrapper.add(emptyText("No cinema revenue yet."));
            return wrapper;
        }

        double max = rows.stream()
                .map(ManagerDashboardService.CinemaRevenueRow::revenue)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(1);

        for (ManagerDashboardService.CinemaRevenueRow row : rows.stream().limit(8).toList()) {
            wrapper.add(barRow(row.cinemaName(), row.ticketsSold() + " tickets · " + money(row.revenue()), row.revenue().doubleValue(), max, BLUE));
        }

        return wrapper;
    }

    private Component buildStatusBars(List<ManagerDashboardService.StatusRow> rows) {
        Div wrapper = new Div();
        wrapper.getStyle().set("display", "grid").set("gap", "14px");

        if (rows == null || rows.isEmpty()) {
            wrapper.add(emptyText("No booking status data."));
            return wrapper;
        }

        double max = rows.stream().mapToDouble(ManagerDashboardService.StatusRow::count).max().orElse(1);
        for (ManagerDashboardService.StatusRow row : rows) {
            String color = "CANCELLED".equals(row.status()) ? RED : GREEN;
            wrapper.add(barRow(row.status(), row.count() + " bookings", row.count(), max, color));
        }
        return wrapper;
    }

    private Div barRow(String title, String subtitle, double value, double max, String color) {
        Div row = new Div();
        row.getStyle().set("display", "grid").set("gap", "7px");

        Div top = new Div();
        top.getStyle().set("display", "flex").set("justify-content", "space-between").set("gap", "12px");

        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("font-weight", "900").set("color", "white");
        Span subtitleSpan = new Span(subtitle);
        subtitleSpan.getStyle().set("font-weight", "800").set("color", "#a8b3c7").set("font-size", "13px");
        top.add(titleSpan, subtitleSpan);

        Div track = new Div();
        track.getStyle()
                .set("height", "12px")
                .set("border-radius", "999px")
                .set("background", "rgba(255,255,255,0.08)")
                .set("overflow", "hidden");

        Div fill = new Div();
        double pct = max <= 0 ? 0 : Math.max(4, (value / max) * 100);
        fill.getStyle()
                .set("height", "100%")
                .set("width", String.format(Locale.UK, "%.1f%%", pct))
                .set("background", color)
                .set("border-radius", "999px");
        track.add(fill);

        row.add(top, track);
        return row;
    }

    private Div legendRow(String color, String title, String value) {
        Div row = new Div();
        row.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "14px 1fr")
                .set("gap", "10px")
                .set("align-items", "start");

        Span dot = new Span();
        dot.getStyle()
                .set("width", "12px")
                .set("height", "12px")
                .set("border-radius", "50%")
                .set("background", color)
                .set("margin-top", "4px");

        Div text = new Div();
        Span name = new Span(title);
        name.getStyle().set("display", "block").set("font-weight", "900").set("color", "white");
        Span amount = new Span(value);
        amount.getStyle().set("display", "block").set("font-size", "13px").set("color", "#a8b3c7").set("margin-top", "3px");
        text.add(name, amount);

        row.add(dot, text);
        return row;
    }

    private Div buildFeedbackPanel() {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "22px");

        Div form = panel("Exportable manager feedback", new Div());
        form.removeAll();
        H2 heading = new H2("Exportable manager feedback");
        heading.getStyle().set("margin", "0 0 16px").set("font-size", "22px").set("font-weight", "950");

        Button save = new Button("Save feedback", event -> saveFeedback());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        stylePrimary(save);

        form.add(heading, feedbackTitle, feedbackComment, buttonRow(save));

        Div recent = panel("Recent feedback", buildFeedbackList(dashboardData.recentFeedback()));
        wrapper.add(form, recent);
        return wrapper;
    }

    private Component buildFeedbackList(List<ManagerFeedback> feedbackList) {
        Div list = new Div();
        list.getStyle().set("display", "grid").set("gap", "12px");

        if (feedbackList == null || feedbackList.isEmpty()) {
            list.add(emptyText("No manager feedback has been saved yet."));
            return list;
        }

        for (ManagerFeedback feedback : feedbackList) {
            Div card = new Div();
            card.getStyle()
                    .set("background", PANEL_SOFT)
                    .set("border", "1px solid " + BORDER)
                    .set("border-radius", "16px")
                    .set("padding", "14px");

            Span title = new Span(feedback.getTitle());
            title.getStyle().set("display", "block").set("font-weight", "950").set("color", "white");
            Span meta = new Span((feedback.getManagerName() == null ? "Manager" : feedback.getManagerName())
                    + " · " + feedback.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            meta.getStyle().set("display", "block").set("color", "#94a3b8").set("font-size", "13px").set("margin", "6px 0");
            Paragraph comment = new Paragraph(feedback.getComment());
            comment.getStyle().set("margin", "0").set("color", "#dbeafe").set("line-height", "1.55");

            card.add(title, meta, comment);
            list.add(card);
        }

        return list;
    }

    private void saveFeedback() {
        try {
            dashboardService.saveFeedback(feedbackTitle.getValue(), feedbackComment.getValue());
            feedbackTitle.clear();
            feedbackComment.clear();
            Notification.show("Feedback saved.");
            refreshDashboard();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private Div buttonRow(Button button) {
        Div row = new Div(button);
        row.getStyle().set("display", "flex").set("justify-content", "flex-end").set("margin-top", "16px");
        return row;
    }

    private Paragraph emptyText(String message) {
        Paragraph empty = new Paragraph(message);
        empty.getStyle().set("margin", "0").set("color", "#94a3b8").set("font-weight", "750");
        return empty;
    }

    private void updateExportLink() {
        exportHolder.removeAll();

        StreamResource resource = new StreamResource(
                "manager-dashboard-export.csv",
                () -> new ByteArrayInputStream(
                        dashboardService.exportCsv(startDate.getValue(), endDate.getValue()).getBytes(StandardCharsets.UTF_8)
                )
        );
        resource.setContentType("text/csv");

        Anchor export = new Anchor(resource, "Export report CSV");
        export.getElement().setAttribute("download", true);
        export.getStyle()
                .set("height", "46px")
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("padding", "0 22px")
                .set("border-radius", "999px")
                .set("background", BLUE)
                .set("color", "white")
                .set("font-weight", "900")
                .set("text-decoration", "none")
                .set("box-shadow", "0 12px 30px rgba(0,114,206,0.24)");
        exportHolder.add(export);
    }

    private String money(BigDecimal value) {
        return MONEY.format(value == null ? BigDecimal.ZERO : value);
    }

    private void styleInput(Component component) {
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
