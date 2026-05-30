package com.eduaccess.ui;

import com.eduaccess.domain.DeliveryMethod;
import com.eduaccess.domain.FoodOrder;
import com.eduaccess.domain.FoodOrderItem;
import com.eduaccess.domain.FoodOrderStatus;
import com.eduaccess.service.FoodOrderService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Route(value = "staff/food-orders", layout = MainLayout.class)
@PageTitle("HCBS — Food Orders")
@CssImport("./styles/food-orders-pro.css")
public class FoodOrdersView extends Div implements BeforeEnterObserver {

    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.UK);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.UK);

    private final FoodOrderService foodOrderService;
    private final LoginService loginService;

    private final Div kpiArea = new Div();
    private final Div insightArea = new Div();
    private final Div pipelineArea = new Div();
    private final Div ordersArea = new Div();

    private final TextField searchField = new TextField();
    private final ComboBox<FoodOrderStatus> statusFilter = new ComboBox<>();
    private Button openButton;
    private Button allButton;

    private boolean showOnlyOpen = true;

    public FoodOrdersView(FoodOrderService foodOrderService, LoginService loginService) {
        this.foodOrderService = foodOrderService;
        this.loginService = loginService;

        setWidthFull();
        addClassName("fop-page");

        Div shell = div("fop-shell fop-shell-compact");
        shell.add(buildControls(), kpiArea, insightArea, pipelineArea, buildOrdersHeader(), ordersArea);
        add(shell);

        reloadOrders();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkBookingAccess(event, loginService);
    }

    private Div buildHero() {
        Div hero = div("fop-hero");

        Div titleBlock = div("fop-title-block");
        Span eyebrow = span("Concessions operations", "fop-eyebrow");
        H1 title = new H1("Food Orders");
        title.addClassName("fop-title");
        Paragraph subtitle = new Paragraph("Live kitchen queue for counter pickup and deliver-to-seat concessions.");
        subtitle.addClassName("fop-subtitle");
        titleBlock.add(eyebrow, title, subtitle);

        Div profile = div("fop-profile-card");
        Span avatar = span(currentUserInitial(), "fop-avatar");
        Div copy = div("fop-profile-copy");
        copy.add(span(currentUserName(), "fop-profile-name"), span("Food operations desk", "fop-profile-role"));
        profile.add(avatar, copy);

        hero.add(titleBlock, profile);
        return hero;
    }

    private Div buildControls() {
        Div controls = div("fop-controls-card fop-glow-card");

        searchField.setPlaceholder("Search booking, film, cinema, seat, items...");
        searchField.setClearButtonVisible(true);
        searchField.addClassName("fop-search");
        searchField.addValueChangeListener(event -> reloadOrders());

        statusFilter.setPlaceholder("Status");
        statusFilter.setItems(FoodOrderStatus.values());
        statusFilter.setItemLabelGenerator(FoodOrderStatus::getLabel);
        statusFilter.setClearButtonVisible(true);
        statusFilter.addClassName("fop-status-filter");
        statusFilter.addValueChangeListener(event -> reloadOrders());

        openButton = controlButton("Open queue", () -> {
            showOnlyOpen = true;
            reloadOrders();
        });
        allButton = controlButton("All orders", () -> {
            showOnlyOpen = false;
            reloadOrders();
        });

        Button refresh = controlButton("Refresh", this::reloadOrders);
        refresh.addClassName("fop-refresh-btn");

        controls.add(searchField, statusFilter, openButton, allButton, refresh);
        return controls;
    }

    private Div buildOrdersHeader() {
        Div header = div("fop-section-header");
        Div copy = div("fop-section-copy");
        copy.add(span("Live order board", "fop-section-title"), span("Prioritise pending orders, prepare active items, and close delivered orders.", "fop-section-subtitle"));
        header.add(copy);
        return header;
    }

    private void reloadOrders() {
        List<FoodOrder> allOrders = safeOrders(foodOrderService.findAllOrders());
        List<FoodOrder> baseOrders = showOnlyOpen
                ? safeOrders(foodOrderService.findOpenOrders())
                : allOrders;

        List<FoodOrder> visibleOrders = applyFilters(baseOrders);

        updateToggleState();
        renderKpis(allOrders);
        renderInsights(allOrders);
        renderPipeline(allOrders);
        renderOrders(visibleOrders);
    }

    private List<FoodOrder> safeOrders(List<FoodOrder> orders) {
        return orders == null ? List.of() : orders;
    }

    private List<FoodOrder> applyFilters(List<FoodOrder> orders) {
        String keyword = searchField.getValue() == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        FoodOrderStatus selectedStatus = statusFilter.getValue();

        return orders.stream()
                .filter(order -> selectedStatus == null || order.getStatus() == selectedStatus)
                .filter(order -> keyword.isBlank() || searchableText(order).contains(keyword))
                .sorted(Comparator
                        .comparing((FoodOrder order) -> priority(order.getStatus()))
                        .thenComparing(FoodOrder::getOrderTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private String searchableText(FoodOrder order) {
        return String.join(" ",
                        safe(bookingRef(order)),
                        safe(filmTitle(order)),
                        safe(cinemaName(order)),
                        safe(screenText(order)),
                        safe(seats(order)),
                        safe(itemText(order)),
                        safe(order.getStatus() == null ? "" : order.getStatus().getLabel()),
                        safe(order.getDeliveryMethod() == null ? "" : order.getDeliveryMethod().getLabel())
                )
                .toLowerCase(Locale.ROOT);
    }

    private int priority(FoodOrderStatus status) {
        if (status == FoodOrderStatus.PENDING) return 0;
        if (status == FoodOrderStatus.PREPARING) return 1;
        if (status == FoodOrderStatus.DELIVERED) return 2;
        return 3;
    }

    private void updateToggleState() {
        if (openButton == null || allButton == null) {
            return;
        }
        openButton.removeClassName("is-active");
        allButton.removeClassName("is-active");
        if (showOnlyOpen) {
            openButton.addClassName("is-active");
        } else {
            allButton.addClassName("is-active");
        }
    }

    private void renderKpis(List<FoodOrder> orders) {
        kpiArea.removeAll();
        kpiArea.addClassName("fop-kpi-grid");

        long pending = count(orders, FoodOrderStatus.PENDING);
        long preparing = count(orders, FoodOrderStatus.PREPARING);
        long delivered = count(orders, FoodOrderStatus.DELIVERED);
        long cancelled = count(orders, FoodOrderStatus.CANCELLED);
        BigDecimal activeValue = total(orders.stream()
                .filter(order -> order.getStatus() == FoodOrderStatus.PENDING || order.getStatus() == FoodOrderStatus.PREPARING)
                .toList());
        BigDecimal deliveredValue = total(orders.stream()
                .filter(order -> order.getStatus() == FoodOrderStatus.DELIVERED)
                .toList());

        kpiArea.add(
                metricCard("Pending", String.valueOf(pending), "Awaiting kitchen start", "+", "gold"),
                metricCard("Preparing", String.valueOf(preparing), "Currently in progress", "•", "blue"),
                metricCard("Delivered", String.valueOf(delivered), "Closed successful orders", "✓", "green"),
                metricCard("Cancelled", String.valueOf(cancelled), "Stopped before fulfilment", "!", "red"),
                metricCard("Active value", MONEY.format(activeValue), "Pending + preparing revenue", "£", "gold"),
                metricCard("Delivered value", MONEY.format(deliveredValue), "Completed concessions income", "Σ", "purple")
        );
    }

    private Div metricCard(String label, String value, String note, String icon, String tone) {
        Div card = div("fop-metric-card tone-" + tone);
        card.add(span(icon, "fop-metric-icon"), span(label, "fop-metric-label"), span(value, "fop-metric-value"), span(note, "fop-metric-note"));
        return card;
    }

    private void renderInsights(List<FoodOrder> orders) {
        insightArea.removeAll();
        insightArea.addClassName("fop-insight-grid");
        insightArea.add(buildOperationsBrief(orders), buildDecisionCentre(orders));
    }

    private Div buildOperationsBrief(List<FoodOrder> orders) {
        long active = orders.stream()
                .filter(order -> order.getStatus() == FoodOrderStatus.PENDING || order.getStatus() == FoodOrderStatus.PREPARING)
                .count();
        long seatDelivery = orders.stream()
                .filter(order -> order.getDeliveryMethod() == DeliveryMethod.DELIVER_TO_SEAT)
                .count();
        long counter = orders.stream()
                .filter(order -> order.getDeliveryMethod() == DeliveryMethod.COUNTER_PICKUP)
                .count();
        FoodOrder oldest = orders.stream()
                .filter(order -> order.getStatus() == FoodOrderStatus.PENDING || order.getStatus() == FoodOrderStatus.PREPARING)
                .filter(order -> order.getOrderTime() != null)
                .min(Comparator.comparing(FoodOrder::getOrderTime))
                .orElse(null);

        Div card = div("fop-large-panel fop-glow-card");
        card.add(span("Operations brief", "fop-panel-kicker"));
        card.add(span(active + " active order" + (active == 1 ? "" : "s"), "fop-panel-title"));
        card.add(span("Live overview of queue pressure, delivery mix and service priority.", "fop-panel-copy"));

        Div bullets = div("fop-brief-list");
        bullets.add(briefItem("Queue pressure", active == 0 ? "Clear" : active <= 3 ? "Manageable" : "Busy", active <= 3 ? "good" : "warn"));
        bullets.add(briefItem("Deliver to seat", seatDelivery + " order" + (seatDelivery == 1 ? "" : "s"), "gold"));
        bullets.add(briefItem("Counter pickup", counter + " order" + (counter == 1 ? "" : "s"), "blue"));
        bullets.add(briefItem("Oldest open order", oldest == null ? "No active wait" : ageText(oldest), oldest == null ? "good" : "warn"));
        card.add(bullets);
        return card;
    }

    private Div buildDecisionCentre(List<FoodOrder> orders) {
        Div card = div("fop-large-panel fop-glow-card");
        card.add(span("Decision centre", "fop-panel-kicker"));
        card.add(span("Recommended actions", "fop-panel-title"));
        card.add(span("Rule-based suggestions generated from current food-order operations.", "fop-panel-copy"));

        Div list = div("fop-action-list");
        for (String action : recommendations(orders)) {
            list.add(actionItem(action));
        }
        card.add(list);
        return card;
    }

    private Div briefItem(String label, String value, String tone) {
        Div item = div("fop-brief-item tone-" + tone);
        item.add(span(label, "fop-brief-label"), span(value, "fop-brief-value"));
        return item;
    }

    private Div actionItem(String text) {
        Div item = div("fop-action-item");
        item.add(span("→", "fop-action-arrow"), span(text, "fop-action-text"));
        return item;
    }

    private List<String> recommendations(List<FoodOrder> orders) {
        long pending = count(orders, FoodOrderStatus.PENDING);
        long preparing = count(orders, FoodOrderStatus.PREPARING);
        long active = pending + preparing;
        long seatDelivery = orders.stream().filter(order -> order.getDeliveryMethod() == DeliveryMethod.DELIVER_TO_SEAT).count();
        long delivered = count(orders, FoodOrderStatus.DELIVERED);
        long cancelled = count(orders, FoodOrderStatus.CANCELLED);

        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        if (active == 0) {
            actions.add("Queue is clear. Keep staff on standby for the next screening interval.");
        } else if (pending >= 4) {
            actions.add("Pending queue is high. Start preparation for oldest orders before accepting new counter tasks.");
        } else {
            actions.add("Queue pressure is manageable. Prioritise deliver-to-seat orders before showtime.");
        }

        if (seatDelivery > 0) {
            actions.add("Assign one runner to seat delivery so kitchen preparation and delivery do not block each other.");
        } else {
            actions.add("No seat-delivery orders currently. Counter pickup can be handled by one staff member.");
        }

        if (cancelled > delivered && cancelled > 0) {
            actions.add("Cancellation volume is higher than delivered orders. Review stock availability and preparation delay.");
        } else {
            actions.add("Fulfilment pattern is stable. Continue monitoring order age and active value.");
        }

        return actions;
    }

    private void renderPipeline(List<FoodOrder> orders) {
        pipelineArea.removeAll();
        pipelineArea.addClassName("fop-pipeline-card");

        long total = Math.max(1, orders.size());
        pipelineArea.add(span("Fulfilment pipeline", "fop-section-title"));
        pipelineArea.add(span("Status distribution across all food orders.", "fop-section-subtitle"));

        Div rows = div("fop-pipeline-rows");
        for (FoodOrderStatus status : FoodOrderStatus.values()) {
            long count = count(orders, status);
            rows.add(pipelineRow(status, count, Math.round((count * 100.0) / total)));
        }
        pipelineArea.add(rows);
    }

    private Div pipelineRow(FoodOrderStatus status, long count, long percent) {
        Div row = div("fop-pipeline-row status-" + status.name().toLowerCase(Locale.ROOT));
        Div top = div("fop-pipeline-top");
        top.add(span(status.getLabel(), "fop-pipeline-name"), span(count + " · " + percent + "%", "fop-pipeline-value"));
        Div track = div("fop-pipeline-track");
        Div fill = div("fop-pipeline-fill");
        fill.getStyle().set("width", Math.max(4, percent) + "%");
        track.add(fill);
        row.add(top, track);
        return row;
    }

    private void renderOrders(List<FoodOrder> orders) {
        ordersArea.removeAll();
        ordersArea.addClassName("fop-orders-grid");

        if (orders.isEmpty()) {
            Div empty = div("fop-empty-state fop-glow-card");
            empty.add(span("No matching food orders", "fop-empty-title"), span(showOnlyOpen ? "The active kitchen queue is clear." : "Try clearing search or status filters.", "fop-empty-copy"));
            ordersArea.add(empty);
            return;
        }

        for (FoodOrder order : orders) {
            ordersArea.add(buildOrderCard(order));
        }
    }

    private Div buildOrderCard(FoodOrder order) {
        FoodOrderStatus status = order.getStatus() == null ? FoodOrderStatus.PENDING : order.getStatus();
        Div card = div("fop-order-card status-" + status.name().toLowerCase(Locale.ROOT));

        Div top = div("fop-order-top");
        Div refBlock = div("fop-order-ref-block");
        H2 ref = new H2(bookingRef(order));
        ref.addClassName("fop-order-ref");
        refBlock.add(ref, span(ageText(order), "fop-order-age"));
        top.add(refBlock, statusBadge(status));

        Div body = div("fop-order-body");
        body.add(infoBlock("Film", filmTitle(order)), infoBlock("Cinema", cinemaName(order)), infoBlock("Screen", screenText(order)), infoBlock("Seats", seats(order)));

        Div items = div("fop-items-box");
        items.add(span("Items", "fop-mini-label"));
        items.add(span(itemText(order), "fop-items-text"));

        Div footer = div("fop-order-footer");
        footer.add(
                pill(order.getDeliveryMethod() == null ? "Delivery unknown" : order.getDeliveryMethod().getLabel(), "delivery"),
                pill(MONEY.format(totalCost(order)), "money"),
                pill(order.getOrderTime() == null ? "Time unknown" : order.getOrderTime().format(DATE_TIME), "time")
        );

        Div actions = div("fop-order-actions");
        if (status == FoodOrderStatus.PENDING) {
            actions.add(actionButton("Start preparing", () -> runAction(() -> foodOrderService.markPreparing(order.getId()), "Food order marked as preparing.")));
        }

        if (status == FoodOrderStatus.PENDING || status == FoodOrderStatus.PREPARING) {
            actions.add(actionButton("Mark delivered", () -> runAction(() -> foodOrderService.markDelivered(order.getId()), "Food order marked as delivered.")));
            actions.add(dangerButton("Cancel", () -> runAction(() -> foodOrderService.cancelOrder(order.getId()), "Food order cancelled.")));
        }

        if (actions.getChildren().findAny().isEmpty()) {
            actions.add(span("No action required", "fop-no-action"));
        }

        card.add(top, body, items, footer, actions);
        return card;
    }

    private Div infoBlock(String label, String value) {
        Div box = div("fop-info-block");
        box.add(span(label, "fop-mini-label"), span(value, "fop-info-value"));
        return box;
    }

    private Span statusBadge(FoodOrderStatus status) {
        Span badge = span(status.getLabel(), "fop-status-badge status-" + status.name().toLowerCase(Locale.ROOT));
        return badge;
    }

    private Span pill(String text, String tone) {
        return span(text, "fop-pill tone-" + tone);
    }

    private Button actionButton(String text, Runnable action) {
        Button button = new Button(text, event -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.addClassName("fop-action-btn");
        return button;
    }

    private Button dangerButton(String text, Runnable action) {
        Button button = new Button(text, event -> action.run());
        button.addClassName("fop-danger-btn");
        return button;
    }

    private Button controlButton(String text, Runnable action) {
        Button button = new Button(text, event -> action.run());
        button.addClassName("fop-control-btn");
        return button;
    }

    private void runAction(Runnable action, String message) {
        try {
            action.run();
            Notification notification = Notification.show(message, 2200, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            reloadOrders();
        } catch (RuntimeException ex) {
            Notification notification = Notification.show(ex.getMessage(), 4200, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private long count(List<FoodOrder> orders, FoodOrderStatus status) {
        return orders.stream().filter(order -> order.getStatus() == status).count();
    }

    private BigDecimal total(List<FoodOrder> orders) {
        return orders.stream()
                .map(this::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalCost(FoodOrder order) {
        return order.getTotalCost() == null ? BigDecimal.ZERO : order.getTotalCost();
    }

    private String itemText(FoodOrder order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return "No items";
        }
        return order.getItems().stream()
                .filter(Objects::nonNull)
                .map(this::itemLine)
                .collect(Collectors.joining("\n"));
    }

    private String itemLine(FoodOrderItem item) {
        String name = item.getFoodItem() == null ? "Item" : item.getFoodItem().getName();
        return name + " ×" + item.getQuantity();
    }

    private String bookingRef(FoodOrder order) {
        return order.getBooking() == null ? "ORDER #" + order.getId() : safe(order.getBooking().getBookingReference());
    }

    private String filmTitle(FoodOrder order) {
        if (order.getBooking() == null || order.getBooking().getScreening() == null || order.getBooking().getScreening().getFilm() == null) {
            return "Film unavailable";
        }
        return safe(order.getBooking().getScreening().getFilm().getTitle());
    }

    private String cinemaName(FoodOrder order) {
        if (order.getBooking() == null
                || order.getBooking().getScreening() == null
                || order.getBooking().getScreening().getScreen() == null
                || order.getBooking().getScreening().getScreen().getCinema() == null) {
            return "Cinema unavailable";
        }
        return safe(order.getBooking().getScreening().getScreen().getCinema().getName());
    }

    private String screenText(FoodOrder order) {
        if (order.getBooking() == null || order.getBooking().getScreening() == null || order.getBooking().getScreening().getScreen() == null) {
            return "Screen -";
        }
        return "Screen " + order.getBooking().getScreening().getScreen().getScreenNumber();
    }

    private String seats(FoodOrder order) {
        if (order.getBooking() == null || order.getBooking().getId() == null) {
            return "-";
        }
        return foodOrderService.findSeatNumbersForBooking(order.getBooking().getId());
    }

    private String ageText(FoodOrder order) {
        if (order == null || order.getOrderTime() == null) {
            return "Time unknown";
        }
        Duration age = Duration.between(order.getOrderTime(), LocalDateTime.now());
        if (age.toMinutes() < 1) {
            return "Just now";
        }
        if (age.toHours() < 1) {
            return age.toMinutes() + " min ago";
        }
        if (age.toDays() < 1) {
            return age.toHours() + " h ago";
        }
        return age.toDays() + " d ago";
    }

    private String currentUserName() {
        try {
            Object user = com.vaadin.flow.server.VaadinSession.getCurrent().getAttribute("currentUser");
            if (user instanceof com.eduaccess.domain.UserAccount account && account.getUsername() != null) {
                return account.getUsername();
            }
        } catch (Exception ignored) {
        }
        return "Staff";
    }

    private String currentUserInitial() {
        String username = currentUserName();
        return username.isBlank() ? "S" : username.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String safe(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private Div div(String classNames) {
        Div div = new Div();
        addClasses(div, classNames);
        return div;
    }

    private Span span(String text, String classNames) {
        Span span = new Span(text == null ? "" : text);
        addClasses(span, classNames);
        return span;
    }

    private void addClasses(com.vaadin.flow.component.HasStyle component, String classNames) {
        if (classNames == null || classNames.isBlank()) {
            return;
        }
        for (String className : classNames.trim().split("\\s+")) {
            component.addClassName(className);
        }
    }
}
