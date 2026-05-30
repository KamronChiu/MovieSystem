package com.eduaccess.ui;

import com.eduaccess.domain.ManagerFeedback;
import com.eduaccess.service.LoginService;
import com.eduaccess.service.ManagerDashboardService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Route(value = "manager/dashboard", layout = MainLayout.class)
@PageTitle("HCBS — Manager Dashboard")
@CssImport("./styles/manager-dashboard-pro-enhanced.css")
public class ManagerDashboardView extends Div implements BeforeEnterObserver {

    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.UK);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM", Locale.UK);
    private static final DateTimeFormatter FEEDBACK_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.UK);

    private static final String GOLD = "#d6aa42";
    private static final String GOLD_SOFT = "#f4d47b";
    private static final String BLUE = "#7c8cff";
    private static final String CYAN = "#67e8f9";
    private static final String GREEN = "#34d399";
    private static final String RED = "#fb7185";
    private static final String PURPLE = "#a78bfa";

    private final LoginService loginService;
    private final ManagerDashboardService dashboardService;

    private final DatePicker startDate = new DatePicker("Start date");
    private final DatePicker endDate = new DatePicker("End date");
    private final Div exportHolder = new Div();
    private final Div content = new Div();
    private final Div briefCard = new Div();
    private final Div decisionCard = new Div();
    private final TextField feedbackTitle = new TextField("Feedback title");
    private final TextArea feedbackComment = new TextArea("Manager feedback / interpretation");

    private ManagerDashboardService.DashboardData dashboardData;

    public ManagerDashboardView(LoginService loginService, ManagerDashboardService dashboardService) {
        this.loginService = loginService;
        this.dashboardService = dashboardService;

        setWidthFull();
        addClassName("manager-dashboard-pro");

        configureFields();
        briefCard.addClassNames("mdp-brief-card", "mdp-glow-card");
        decisionCard.addClassNames("mdp-decision-card", "mdp-glow-card");

        Div page = div("mdp-page");
        page.add(buildTopBar(), buildHero(), content);
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

        startDate.addClassName("mdp-field");
        endDate.addClassName("mdp-field");

        feedbackTitle.setPlaceholder("e.g. Weekly sales review");
        feedbackTitle.setWidthFull();
        feedbackTitle.addClassName("mdp-field");

        feedbackComment.setPlaceholder("Explain why performance changed, what films need more screenings, or what customer demand suggests.");
        feedbackComment.setWidthFull();
        feedbackComment.setMinHeight("155px");
        feedbackComment.addClassName("mdp-field");
    }

    private Div buildTopBar() {
        Div top = div("mdp-topbar");

        Div titleBlock = div("mdp-title-block");
        H1 title = new H1("Dashboard");
        title.addClassName("mdp-page-title");
        Span subtitle = span("Manager command center ", "mdp-page-subtitle");
        titleBlock.add(title, subtitle);

        Div search = div("mdp-search");
        search.add(span("⌕", "mdp-search-icon"), span("Search film, cinema, booking insight...", "mdp-search-text"), span("alt+f", "mdp-search-key"));

        Div profile = div("mdp-profile");
        Span avatar = span(initials(safeCurrentManagerName()), "mdp-avatar");
        Div profileText = div("mdp-profile-text");
        profileText.add(span(safeCurrentManagerName(), "mdp-profile-name"), span("Manager account", "mdp-profile-email"));
        profile.add(avatar, profileText);

        top.add(titleBlock, search, exportHolder, profile);
        return top;
    }

    private Div buildHero() {
        Div hero = div("mdp-hero-grid");

        Div greeting = div("mdp-greeting-card mdp-glow-card");
        greeting.add(
                span("Good morning,", "mdp-muted"),
                span(safeCurrentManagerName(), "mdp-greeting-name"),
                span("Per Aspera Ad Astra", "mdp-greeting-copy"),
                buildControls()
        );

        briefCard.add(buildLoadingCard("Management brief", "The executive summary will update after dashboard data loads."));
        decisionCard.add(buildLoadingCard("Decision centre", "Recommended actions will update from ticket, food and cancellation results."));

        hero.add(greeting, briefCard, decisionCard);
        return hero;
    }

    private Div buildLoadingCard(String title, String copy) {
        Div body = div("mdp-mini-loading");
        body.add(span(title, "mdp-card-eyebrow"), span(copy, "mdp-small-copy"));
        return body;
    }

    private Div buildControls() {
        Div controls = div("mdp-controls");

        Button apply = new Button("Apply", event -> refreshDashboard());
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        apply.addClassName("mdp-primary-btn");

        Button week = new Button("7D", event -> applyPreset(7));
        Button month = new Button("30D", event -> applyPreset(30));
        Button quarter = new Button("90D", event -> applyPreset(90));
        week.addClassName("mdp-chip-btn");
        month.addClassName("mdp-chip-btn");
        quarter.addClassName("mdp-chip-btn");

        controls.add(startDate, endDate, apply, week, month, quarter);
        return controls;
    }

    private void applyPreset(int days) {
        startDate.setValue(LocalDate.now().minusDays(days));
        endDate.setValue(LocalDate.now());
        refreshDashboard();
    }

    private void refreshDashboard() {
        if (startDate.getValue() != null && endDate.getValue() != null && startDate.getValue().isAfter(endDate.getValue())) {
            Notification.show("Start date cannot be after end date.");
            return;
        }

        dashboardData = dashboardService.buildDashboard(startDate.getValue(), endDate.getValue());
        updateHeroInsightCards();
        content.removeAll();
        content.add(
                buildExecutiveSummary(dashboardData.summary()),
                buildWorkspaceGrid(),
                buildOperationsGrid(),
                buildFeedbackPanel()
        );
        updateExportLink();
    }

    private void updateHeroInsightCards() {
        briefCard.removeAll();
        decisionCard.removeAll();

        Div briefHead = div("mdp-brief-head");
        briefHead.add(
                span("Management brief", "mdp-card-eyebrow"),
                span(startDate.getValue().format(SHORT_DATE) + " — " + endDate.getValue().format(SHORT_DATE), "mdp-period-value")
        );

        Div briefRows = div("mdp-brief-list");
        for (ManagerDashboardService.ExecutiveBriefRow row : dashboardData.executiveBrief()) {
            briefRows.add(briefRow(row));
        }
        briefCard.add(briefHead, briefRows);

        decisionCard.add(span("Decision centre", "mdp-card-eyebrow"), span("Recommended next moves", "mdp-action-title"));
        Div actionList = div("mdp-decision-list");
        for (ManagerDashboardService.DecisionActionRow action : dashboardData.decisionActions()) {
            actionList.add(decisionRow(action));
        }
        decisionCard.add(actionList);
    }

    private Div briefRow(ManagerDashboardService.ExecutiveBriefRow row) {
        Div item = div("mdp-brief-row mdp-tone-" + safeTone(row.tone()));
        item.add(span(row.label(), "mdp-brief-label"), span(row.value(), "mdp-brief-value"));
        return item;
    }

    private Div decisionRow(ManagerDashboardService.DecisionActionRow action) {
        Div item = div("mdp-decision-row mdp-tone-" + safeTone(action.tone()));
        Div top = div("mdp-decision-top");
        top.add(span(action.priority(), "mdp-decision-priority"), span(action.title(), "mdp-decision-title"));
        item.add(top, span(action.description(), "mdp-decision-copy"));
        return item;
    }

    private Div buildExecutiveSummary(ManagerDashboardService.Summary summary) {
        Div wrap = div("mdp-summary-grid");

        BigDecimal averageOrder = summary.confirmedBookings() == 0
                ? BigDecimal.ZERO
                : summary.grandRevenue().divide(BigDecimal.valueOf(summary.confirmedBookings()), 2, RoundingMode.HALF_UP);

        wrap.add(
                metricCard("Grand revenue", money(summary.grandRevenue()), "Tickets + concessions", GOLD, "+"),
                metricCard("Tickets sold", String.valueOf(summary.ticketsSold()), "Confirmed seat sales", BLUE, "▦"),
                metricCard("Ticket revenue", money(summary.ticketRevenue()), "Box office only", GREEN, "£"),
                metricCard("Food revenue", money(summary.foodRevenue()), "Concessions income", GOLD_SOFT, "◐"),
                metricCard("Cancel rate", summary.cancelRate() + "%", "Cancelled / total", RED, "!"),
                metricCard("Avg order", money(averageOrder), "Revenue per booking", PURPLE, "∑")
        );
        return wrap;
    }

    private Div metricCard(String label, String value, String hint, String accent, String icon) {
        Div card = div("mdp-metric-card");
        card.getStyle().set("--accent", accent);
        card.add(
                span(icon, "mdp-metric-icon"),
                span(label, "mdp-metric-label"),
                span(value, "mdp-metric-value"),
                span(hint, "mdp-metric-hint")
        );
        return card;
    }

    private Div buildWorkspaceGrid() {
        Div grid = div("mdp-workspace-grid");

        Div revenuePanel = panel(
                "Revenue trend",
                "Daily revenue with peak-day and average performance",
                buildRevenueTrendCard(dashboardData.dailyRevenue()),
                "mdp-panel-span-2 mdp-revenue-panel"
        );
        Div mixPanel = panel("Revenue mix", "Ticket versus food contribution", buildRevenueMixCard(), "mdp-panel-compact");
        Div heatmapPanel = panel(
                "Showtime heatmap",
                "Compact demand map by weekday and time slot",
                buildShowtimeHeatmap(),
                "mdp-panel-span-2 mdp-heatmap-panel"
        );
        Div filmPanel = panel("Film share", "Top films by revenue", buildDonutChart(dashboardData.filmSales()), "mdp-panel-compact");
        Div topFilmPanel = panel("Top film board", "Fast read of customer demand", buildFilmLeaderboard(dashboardData.filmSales()), "mdp-panel-compact");

        grid.add(revenuePanel, mixPanel, heatmapPanel, filmPanel, topFilmPanel);
        return grid;
    }

    private Div buildOperationsGrid() {
        Div grid = div("mdp-ops-grid");
        grid.add(
                panel("Cinema performance", "Revenue and ticket volume by cinema", buildCinemaBars(dashboardData.cinemaRevenue()), "mdp-panel-wide"),
                panel("Booking status", "Operational health and cancellations", buildStatusBoard(dashboardData.bookingStatus()), ""),
                panel("Management focus", "Automatic interpretation from current dashboard", buildInsightCards(), "")
        );
        return grid;
    }

    private Div panel(String title, String subtitle, Component body, String extraClass) {
        Div panel = div("mdp-panel");
        if (extraClass != null && !extraClass.isBlank()) {
            for (String cls : extraClass.split(" ")) {
                if (!cls.isBlank()) {
                    panel.addClassName(cls.trim());
                }
            }
        }

        Div head = div("mdp-panel-head");
        Div text = div("mdp-panel-title-wrap");
        text.add(span(title, "mdp-panel-title"), span(subtitle, "mdp-panel-subtitle"));
        head.add(text, span("●", "mdp-panel-dot"));

        panel.add(head, body);
        return panel;
    }

    private Component buildRevenueTrendCard(List<ManagerDashboardService.DailyRevenueRow> rows) {
        Div box = div("mdp-revenue-trend-box");
        box.add(buildLineChart(rows));

        Div stats = div("mdp-trend-stat-grid");
        BigDecimal periodTotal = rows == null
                ? BigDecimal.ZERO
                : rows.stream()
                .map(ManagerDashboardService.DailyRevenueRow::totalRevenue)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ManagerDashboardService.DailyRevenueRow peak = rows == null
                ? null
                : rows.stream()
                .max(Comparator.comparing(row -> row.totalRevenue() == null ? BigDecimal.ZERO : row.totalRevenue()))
                .orElse(null);

        long activeDays = rows == null ? 0 : rows.stream()
                .filter(row -> row.totalRevenue() != null && row.totalRevenue().compareTo(BigDecimal.ZERO) > 0)
                .count();

        BigDecimal average = rows == null || rows.isEmpty()
                ? BigDecimal.ZERO
                : periodTotal.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);

        BigDecimal ticketTotal = rows == null
                ? BigDecimal.ZERO
                : rows.stream()
                .map(ManagerDashboardService.DailyRevenueRow::ticketRevenue)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal foodTotal = rows == null
                ? BigDecimal.ZERO
                : rows.stream()
                .map(ManagerDashboardService.DailyRevenueRow::foodRevenue)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double foodShare = periodTotal.compareTo(BigDecimal.ZERO) == 0
                ? 0
                : foodTotal.multiply(BigDecimal.valueOf(100)).divide(periodTotal, 1, RoundingMode.HALF_UP).doubleValue();

        stats.add(
                trendStat("Period total", money(periodTotal), "Tickets + concessions", GOLD),
                trendStat("Peak day", peak == null ? "No data" : peak.date().format(SHORT_DATE), peak == null ? "-" : money(peak.totalRevenue()), BLUE),
                trendStat("Average / day", money(average), activeDays + " active sales days", GREEN),
                trendStat("Food share", String.format(Locale.UK, "%.1f%%", foodShare), money(foodTotal) + " concessions", CYAN)
        );

        Div legend = div("mdp-chart-legend");
        legend.add(
                legendPill(GOLD, "Total revenue", money(periodTotal)),
                legendPill(BLUE, "Ticket revenue", money(ticketTotal)),
                legendPill(CYAN, "Food revenue", money(foodTotal))
        );

        box.add(stats, legend);
        return box;
    }

    private Div trendStat(String label, String value, String hint, String color) {
        Div stat = div("mdp-trend-stat");
        stat.getStyle().set("--accent", color);
        stat.add(span(label, "mdp-trend-stat-label"), span(value, "mdp-trend-stat-value"), span(hint, "mdp-trend-stat-hint"));
        return stat;
    }

    private Div legendPill(String color, String label, String value) {
        Div pill = div("mdp-legend-pill");
        pill.getStyle().set("--accent", color);
        pill.add(span("", "mdp-legend-dot-small"), span(label, "mdp-legend-pill-label"), span(value, "mdp-legend-pill-value"));
        return pill;
    }

    private Component buildLineChart(List<ManagerDashboardService.DailyRevenueRow> rows) {
        Div wrapper = div("mdp-line-chart");

        if (rows == null || rows.isEmpty()) {
            wrapper.add(emptyText("No revenue data for this period."));
            return wrapper;
        }

        double max = rows.stream()
                .map(ManagerDashboardService.DailyRevenueRow::totalRevenue)
                .mapToDouble(value -> value == null ? 0 : value.doubleValue())
                .max()
                .orElse(0);

        int width = 980;
        int height = 310;
        int left = 58;
        int right = 34;
        int top = 28;
        int bottom = 60;
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;

        StringBuilder totalPoints = new StringBuilder();
        StringBuilder ticketPoints = new StringBuilder();
        StringBuilder areaPoints = new StringBuilder();
        StringBuilder dots = new StringBuilder();
        StringBuilder labels = new StringBuilder();
        StringBuilder gridLines = new StringBuilder();

        for (int g = 0; g <= 4; g++) {
            double y = top + (plotHeight * g / 4.0);
            gridLines.append(String.format(Locale.UK,
                    "<line x1='%d' y1='%.1f' x2='%d' y2='%.1f' stroke='rgba(255,255,255,0.08)'/>%n",
                    left, y, left + plotWidth, y));
        }

        for (int i = 0; i < rows.size(); i++) {
            ManagerDashboardService.DailyRevenueRow row = rows.get(i);
            double x = rows.size() == 1
                    ? left + (plotWidth / 2.0)
                    : left + ((double) i / (rows.size() - 1)) * plotWidth;
            double totalY = max <= 0
                    ? top + plotHeight
                    : top + plotHeight - (safeDouble(row.totalRevenue()) / max) * plotHeight;
            double ticketY = max <= 0
                    ? top + plotHeight
                    : top + plotHeight - (safeDouble(row.ticketRevenue()) / max) * plotHeight;

            totalPoints.append(String.format(Locale.UK, "%.1f,%.1f ", x, totalY));
            ticketPoints.append(String.format(Locale.UK, "%.1f,%.1f ", x, ticketY));
            areaPoints.append(String.format(Locale.UK, "%.1f,%.1f ", x, totalY));

            dots.append(String.format(Locale.UK,
                    "<circle cx='%.1f' cy='%.1f' r='5' fill='#d6aa42' stroke='#0b1020' stroke-width='3'><title>%s · %s</title></circle>%n",
                    x, totalY, escapeXml(row.date()), escapeXml(money(row.totalRevenue()))));

            if (i == 0 || i == rows.size() - 1 || i % Math.max(1, rows.size() / 6) == 0) {
                labels.append(String.format(Locale.UK,
                        "<text x='%.1f' y='%d' text-anchor='middle' fill='rgba(231,236,247,0.60)' font-size='12'>%s</text>%n",
                        x, height - 22, escapeXml(row.date().format(SHORT_DATE))));
            }
        }

        String area = areaPoints + String.format(Locale.UK, "%.1f,%.1f %.1f,%.1f",
                (double) (left + plotWidth), (double) (top + plotHeight), (double) left, (double) (top + plotHeight));

        String svg = """
                <svg viewBox='0 0 %d %d' width='100%%' height='330' role='img' aria-label='Revenue trend chart'>
                    <defs>
                        <linearGradient id='goldLine' x1='0' x2='1' y1='0' y2='0'>
                            <stop offset='0%%' stop-color='#d6aa42'/>
                            <stop offset='55%%' stop-color='#f4d47b'/>
                            <stop offset='100%%' stop-color='#7c8cff'/>
                        </linearGradient>
                        <linearGradient id='goldArea' x1='0' x2='0' y1='0' y2='1'>
                            <stop offset='0%%' stop-color='rgba(214,170,66,0.34)'/>
                            <stop offset='100%%' stop-color='rgba(214,170,66,0)'/>
                        </linearGradient>
                    </defs>
                    <rect x='0' y='0' width='%d' height='%d' rx='28' fill='rgba(255,255,255,0.025)'/>
                    %s
                    <line x1='%d' y1='%d' x2='%d' y2='%d' stroke='rgba(255,255,255,0.16)'/>
                    <text x='%d' y='%d' fill='rgba(231,236,247,0.62)' font-size='12'>%s</text>
                    <polygon points='%s' fill='url(#goldArea)'/>
                    <polyline points='%s' fill='none' stroke='rgba(124,140,255,0.55)' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'/>
                    <polyline points='%s' fill='none' stroke='url(#goldLine)' stroke-width='5' stroke-linecap='round' stroke-linejoin='round'/>
                    %s
                    %s
                </svg>
                """.formatted(
                width, height,
                width, height,
                gridLines,
                left, top + plotHeight, left + plotWidth, top + plotHeight,
                left + 4, top + 14, max <= 0 ? "£0" : escapeXml(money(BigDecimal.valueOf(max))),
                area,
                ticketPoints,
                totalPoints,
                dots,
                labels
        );

        wrapper.getElement().setProperty("innerHTML", svg);
        return wrapper;
    }

    private Component buildRevenueMixCard() {
        ManagerDashboardService.Summary s = dashboardData.summary();
        BigDecimal total = s.grandRevenue() == null ? BigDecimal.ZERO : s.grandRevenue();
        double ticketPct = total.compareTo(BigDecimal.ZERO) == 0 ? 0 : safeDouble(s.ticketRevenue()) / safeDouble(total) * 100.0;
        double foodPct = total.compareTo(BigDecimal.ZERO) == 0 ? 0 : safeDouble(s.foodRevenue()) / safeDouble(total) * 100.0;

        Div box = div("mdp-mix-box");
        box.add(
                radialProgress(String.format(Locale.UK, "%.0f", ticketPct), "Ticket share"),
                mixRow("Ticket revenue", money(s.ticketRevenue()), ticketPct, GOLD),
                mixRow("Food revenue", money(s.foodRevenue()), foodPct, CYAN),
                mixRow("Grand total", money(s.grandRevenue()), 100, PURPLE)
        );
        return box;
    }

    private Div radialProgress(String pct, String label) {
        Div radial = div("mdp-radial");
        radial.getStyle().set("--pct", pct + "%");
        radial.add(span(pct + "%", "mdp-radial-value"), span(label, "mdp-radial-label"));
        return radial;
    }

    private Div mixRow(String title, String value, double pct, String color) {
        Div row = div("mdp-mix-row");
        row.getStyle().set("--bar", color).set("--pct", String.format(Locale.UK, "%.1f%%", Math.max(3, pct)));
        Div top = div("mdp-mix-top");
        top.add(span(title, "mdp-mix-title"), span(value, "mdp-mix-value"));
        row.add(top, div("mdp-mix-track"));
        return row;
    }

    private Component buildDonutChart(List<ManagerDashboardService.FilmSalesRow> rows) {
        Div wrapper = div("mdp-donut-wrap");

        if (rows == null || rows.isEmpty()) {
            wrapper.add(emptyText("No film sales yet."));
            return wrapper;
        }

        List<ManagerDashboardService.FilmSalesRow> topRows = rows.stream().limit(5).toList();
        double total = topRows.stream()
                .map(ManagerDashboardService.FilmSalesRow::revenue)
                .mapToDouble(this::safeDouble)
                .sum();

        String[] colors = {GOLD, CYAN, PURPLE, GREEN, RED};
        double cursor = 0;
        StringBuilder gradient = new StringBuilder("conic-gradient(");
        for (int i = 0; i < topRows.size(); i++) {
            double share = total <= 0 ? 100.0 / topRows.size() : safeDouble(topRows.get(i).revenue()) / total * 100.0;
            double end = cursor + share;
            if (i > 0) {
                gradient.append(", ");
            }
            gradient.append(colors[i % colors.length]).append(' ')
                    .append(String.format(Locale.UK, "%.2f%% %.2f%%", cursor, end));
            cursor = end;
        }
        gradient.append(')');

        Div donut = div("mdp-donut");
        donut.getStyle().set("background", gradient.toString());
        Div donutCenter = div("mdp-donut-center");
        donutCenter.add(span(String.valueOf(topRows.size()), "mdp-donut-number"), span("films", "mdp-donut-label"));
        donut.add(donutCenter);

        Div legend = div("mdp-donut-legend");
        for (int i = 0; i < topRows.size(); i++) {
            ManagerDashboardService.FilmSalesRow row = topRows.get(i);
            legend.add(legendRow(colors[i % colors.length], row.filmTitle(), row.ticketsSold() + " tickets · " + money(row.revenue())));
        }

        wrapper.add(donut, legend);
        return wrapper;
    }

    private Component buildFilmLeaderboard(List<ManagerDashboardService.FilmSalesRow> rows) {
        Div list = div("mdp-leaderboard");
        if (rows == null || rows.isEmpty()) {
            list.add(emptyText("No film sales yet."));
            return list;
        }

        int index = 1;
        for (ManagerDashboardService.FilmSalesRow row : rows.stream().limit(5).toList()) {
            Div item = div("mdp-rank-row");
            item.add(
                    span(String.format(Locale.UK, "%02d", index++), "mdp-rank-number"),
                    div("mdp-rank-main", span(row.filmTitle(), "mdp-rank-title"), span(row.ticketsSold() + " tickets", "mdp-rank-sub")),
                    span(money(row.revenue()), "mdp-rank-value")
            );
            list.add(item);
        }
        return list;
    }

    private Component buildShowtimeHeatmap() {
        Div wrapper = div("mdp-heatmap-wrap");
        List<ManagerDashboardService.ShowtimeHeatmapRow> rows = dashboardData.showtimeHeatmap();
        if (rows == null || rows.isEmpty()) {
            wrapper.add(emptyText("No showtime demand data yet."));
            return wrapper;
        }

        long maxTickets = rows.stream()
                .flatMap(row -> row.cells().stream())
                .mapToLong(ManagerDashboardService.ShowtimeHeatmapCell::ticketsSold)
                .max()
                .orElse(0);

        Div heatmap = div("mdp-heatmap-compact");
        heatmap.add(span("Slot", "mdp-heat-head mdp-heat-slot-head"));
        for (ManagerDashboardService.ShowtimeHeatmapRow row : rows) {
            heatmap.add(span(row.day(), "mdp-heat-head mdp-heat-day-head"));
        }

        int slotCount = rows.stream().mapToInt(row -> row.cells().size()).max().orElse(0);
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            String slotLabel = rows.get(0).cells().size() > slotIndex
                    ? rows.get(0).cells().get(slotIndex).slot()
                    : "Slot";
            heatmap.add(span(slotLabel, "mdp-heat-slot-label"));

            for (ManagerDashboardService.ShowtimeHeatmapRow row : rows) {
                ManagerDashboardService.ShowtimeHeatmapCell cell = row.cells().size() > slotIndex
                        ? row.cells().get(slotIndex)
                        : new ManagerDashboardService.ShowtimeHeatmapCell(slotLabel, 0, BigDecimal.ZERO);
                Div block = div("mdp-heat-cell-compact " + heatClass(cell.ticketsSold(), maxTickets));
                block.getElement().setProperty(
                        "title",
                        row.day() + " · " + cell.slot() + " · " + cell.ticketsSold() + " tickets · " + money(cell.revenue())
                );
                block.add(
                        span(String.valueOf(cell.ticketsSold()), "mdp-heat-number"),
                        span(shortMoney(cell.revenue()), "mdp-heat-revenue")
                );
                heatmap.add(block);
            }
        }

        Div peakStrip = div("mdp-heat-peak-strip");
        rows.stream()
                .flatMap(row -> row.cells().stream().map(cell -> new HeatPeak(row.day(), cell.slot(), cell.ticketsSold(), cell.revenue())))
                .sorted(Comparator.comparingLong(HeatPeak::tickets).reversed())
                .limit(3)
                .forEach(peak -> peakStrip.add(heatPeakChip(peak)));

        wrapper.add(heatmap, peakStrip);
        return wrapper;
    }

    private Div heatPeakChip(HeatPeak peak) {
        Div chip = div("mdp-heat-peak-chip");
        chip.add(
                span(peak.day() + " · " + peak.slot(), "mdp-heat-peak-title"),
                span(peak.tickets() + " tickets · " + money(peak.revenue()), "mdp-heat-peak-value")
        );
        return chip;
    }

    private String shortMoney(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        if (safeAmount.abs().compareTo(BigDecimal.valueOf(1000)) >= 0) {
            return "£" + safeAmount.divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP) + "k";
        }
        return MONEY.format(safeAmount);
    }

    private record HeatPeak(String day, String slot, long tickets, BigDecimal revenue) {}

    private String heatClass(long tickets, long maxTickets) {
        if (tickets <= 0 || maxTickets <= 0) {
            return "mdp-heat-empty";
        }
        double ratio = tickets / (double) maxTickets;
        if (ratio >= 0.75) {
            return "mdp-heat-hot";
        }
        if (ratio >= 0.4) {
            return "mdp-heat-mid";
        }
        return "mdp-heat-low";
    }

    private Component buildCinemaBars(List<ManagerDashboardService.CinemaRevenueRow> rows) {
        Div wrapper = div("mdp-bar-list");

        if (rows == null || rows.isEmpty()) {
            wrapper.add(emptyText("No cinema revenue yet."));
            return wrapper;
        }

        double max = rows.stream()
                .map(ManagerDashboardService.CinemaRevenueRow::revenue)
                .mapToDouble(this::safeDouble)
                .max()
                .orElse(1);

        for (ManagerDashboardService.CinemaRevenueRow row : rows.stream().limit(8).toList()) {
            wrapper.add(barRow(row.cinemaName(), row.ticketsSold() + " tickets · " + money(row.revenue()), safeDouble(row.revenue()), max, GOLD));
        }

        return wrapper;
    }

    private Component buildStatusBoard(List<ManagerDashboardService.StatusRow> rows) {
        Div wrapper = div("mdp-status-board");

        if (rows == null || rows.isEmpty()) {
            wrapper.add(emptyText("No booking status data."));
            return wrapper;
        }

        double max = rows.stream().mapToDouble(ManagerDashboardService.StatusRow::count).max().orElse(1);
        for (ManagerDashboardService.StatusRow row : rows) {
            String status = row.status() == null ? "UNKNOWN" : row.status();
            String color = status.contains("CANCEL") ? RED : status.contains("REFUND") ? PURPLE : GREEN;
            wrapper.add(barRow(status, row.count() + " bookings", row.count(), max, color));
        }
        return wrapper;
    }

    private Component buildInsightCards() {
        Div insights = div("mdp-insight-list");
        for (ManagerDashboardService.DecisionActionRow action : dashboardData.decisionActions()) {
            insights.add(insightCard(action.priority() + " · " + action.title(), action.description(), toneColor(action.tone())));
        }
        return insights;
    }

    private Div insightCard(String title, String text, String color) {
        Div card = div("mdp-insight-card");
        card.getStyle().set("--accent", color);
        card.add(span(title, "mdp-insight-title"), span(text, "mdp-insight-text"));
        return card;
    }

    private Div barRow(String title, String subtitle, double value, double max, String color) {
        Div row = div("mdp-bar-row");
        row.getStyle().set("--bar", color).set("--pct", String.format(Locale.UK, "%.1f%%", max <= 0 ? 0 : Math.max(4, value / max * 100)));

        Div top = div("mdp-bar-top");
        top.add(span(title, "mdp-bar-title"), span(subtitle, "mdp-bar-subtitle"));
        row.add(top, div("mdp-bar-track"));
        return row;
    }

    private Div legendRow(String color, String title, String value) {
        Div row = div("mdp-legend-row");
        Span dot = span("", "mdp-legend-dot");
        dot.getStyle().set("background", color);

        Div text = div("mdp-legend-text");
        text.add(span(title, "mdp-legend-title"), span(value, "mdp-legend-value"));
        row.add(dot, text);
        return row;
    }

    private Div buildFeedbackPanel() {
        Div wrapper = div("mdp-feedback-grid");

        Div form = panel("Exportable manager feedback", "Saved notes appear in CSV export", new Div(), "");
        form.addClassName("mdp-feedback-form");
        Button save = new Button("Save feedback", event -> saveFeedback());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClassName("mdp-primary-btn");
        form.add(feedbackTitle, feedbackComment, buttonRow(save));

        Div recent = panel("Recent feedback", "Manager notes and decisions", buildFeedbackList(dashboardData.recentFeedback()), "");
        wrapper.add(form, recent);
        return wrapper;
    }

    private Component buildFeedbackList(List<ManagerFeedback> feedbackList) {
        Div list = div("mdp-feedback-list");

        if (feedbackList == null || feedbackList.isEmpty()) {
            list.add(emptyText("No manager feedback has been saved yet."));
            return list;
        }

        for (ManagerFeedback feedback : feedbackList.stream().limit(6).toList()) {
            Div card = div("mdp-feedback-card");
            String title = feedback.getTitle() == null || feedback.getTitle().isBlank() ? "Manager note" : feedback.getTitle();
            String manager = feedback.getManagerName() == null || feedback.getManagerName().isBlank() ? "Manager" : feedback.getManagerName();
            String createdAt = feedback.getCreatedAt() == null ? "Unknown time" : feedback.getCreatedAt().format(FEEDBACK_DATE);

            card.add(
                    span(title, "mdp-feedback-title"),
                    span(manager + " · " + createdAt, "mdp-feedback-meta"),
                    paragraph(feedback.getComment(), "mdp-feedback-comment")
            );
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
        Div row = div("mdp-button-row");
        row.add(button);
        return row;
    }

    private Paragraph emptyText(String message) {
        Paragraph empty = new Paragraph(message);
        empty.addClassName("mdp-empty");
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

        Anchor export = new Anchor(resource, "Export CSV");
        export.getElement().setAttribute("download", true);
        export.addClassName("mdp-export-link");
        exportHolder.add(export);
    }

    private String safeCurrentManagerName() {
        try {
            if (loginService.getCurrentUser() != null && loginService.getCurrentUser().getFullName() != null) {
                return loginService.getCurrentUser().getFullName();
            }
            if (loginService.getCurrentUser() != null && loginService.getCurrentUser().getUsername() != null) {
                return loginService.getCurrentUser().getUsername();
            }
        } catch (RuntimeException ignored) {
        }
        return "Manager";
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) {
            return "M";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(Locale.UK);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.UK);
    }

    private String money(BigDecimal value) {
        return MONEY.format(value == null ? BigDecimal.ZERO : value);
    }

    private double safeDouble(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private String safeTone(String tone) {
        if (tone == null || tone.isBlank()) {
            return "neutral";
        }
        return tone.toLowerCase(Locale.UK).replaceAll("[^a-z]", "");
    }

    private String toneColor(String tone) {
        return switch (safeTone(tone)) {
            case "positive" -> GREEN;
            case "warning" -> RED;
            default -> GOLD;
        };
    }

    private String escapeXml(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private Div div(String className, Component... children) {
        Div div = new Div();
        if (className != null && !className.isBlank()) {
            for (String cls : className.split(" ")) {
                if (!cls.isBlank()) {
                    div.addClassName(cls.trim());
                }
            }
        }
        if (children != null) {
            div.add(children);
        }
        return div;
    }

    private Span span(String text, String className) {
        Span span = new Span(text == null ? "" : text);
        addClasses(span, className);
        return span;
    }

    private Paragraph paragraph(String text, String className) {
        Paragraph p = new Paragraph(text == null ? "" : text);
        addClasses(p, className);
        return p;
    }

    private void addClasses(Component component, String className) {
        if (component == null || className == null || className.isBlank()) {
            return;
        }
        for (String cls : className.split("\\s+")) {
            if (!cls.isBlank()) {
                component.getElement().getClassList().add(cls.trim());
            }
        }
    }
}
