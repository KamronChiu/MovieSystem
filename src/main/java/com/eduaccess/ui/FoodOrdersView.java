package com.eduaccess.ui;

import com.eduaccess.domain.FoodOrder;
import com.eduaccess.domain.FoodOrderItem;
import com.eduaccess.domain.FoodOrderStatus;
import com.eduaccess.service.FoodOrderService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Route(value = "staff/food-orders", layout = MainLayout.class)
@PageTitle("HCBS — Food Orders")
public class FoodOrdersView extends Div implements BeforeEnterObserver {

    private static final String DARK_BG = "#020b1d";
    private static final String BLUE = "#0072ce";
    private static final String PANEL = "#071629";
    private static final String BORDER = "rgba(255,255,255,0.14)";
    private static final String MUTED = "#94a3b8";

    private final FoodOrderService foodOrderService;
    private final LoginService loginService;
    private final Div ordersArea = new Div();

    private boolean showOnlyOpen = true;

    public FoodOrdersView(FoodOrderService foodOrderService, LoginService loginService) {
        this.foodOrderService = foodOrderService;
        this.loginService = loginService;

        setWidthFull();
        getStyle()
                .set("background", DARK_BG)
                .set("min-height", "100vh")
                .set("color", "white");

        Div page = new Div();
        page.getStyle()
                .set("max-width", "1320px")
                .set("margin", "0 auto")
                .set("padding", "44px 48px 80px 48px")
                .set("box-sizing", "border-box");

        page.add(buildHeader(), ordersArea);
        add(page);

        reloadOrders();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkBookingAccess(event, loginService);
    }

    private Div buildHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "end")
                .set("gap", "24px")
                .set("margin-bottom", "34px")
                .set("flex-wrap", "wrap");

        Div titleBlock = new Div();

        H1 title = new H1("Food Orders");
        title.getStyle()
                .set("font-size", "42px")
                .set("font-weight", "950")
                .set("margin", "0 0 10px 0")
                .set("letter-spacing", "0.04em");

        Paragraph subtitle = new Paragraph("Track counter pickup and deliver-to-seat food orders linked to ticket bookings.");
        subtitle.getStyle()
                .set("color", MUTED)
                .set("font-size", "17px")
                .set("margin", "0");

        titleBlock.add(title, subtitle);

        Button toggle = new Button("Show all orders", event -> {
            showOnlyOpen = !showOnlyOpen;
            event.getSource().setText(showOnlyOpen ? "Show all orders" : "Show open orders");
            reloadOrders();
        });
        toggle.getStyle()
                .set("height", "44px")
                .set("background", "transparent")
                .set("color", "white")
                .set("border", "1px solid rgba(255,255,255,0.35)")
                .set("font-weight", "850")
                .set("border-radius", "0")
                .set("padding", "0 22px");

        header.add(titleBlock, toggle);
        return header;
    }

    private void reloadOrders() {
        ordersArea.removeAll();

        List<FoodOrder> orders = showOnlyOpen
                ? foodOrderService.findOpenOrders()
                : foodOrderService.findAllOrders();

        ordersArea.getStyle()
                .set("display", "grid")
                .set("gap", "18px");

        if (orders.isEmpty()) {
            Div empty = new Div();
            empty.setText(showOnlyOpen ? "No pending or preparing food orders." : "No food orders found.");
            empty.getStyle()
                    .set("padding", "26px")
                    .set("background", PANEL)
                    .set("border", "1px solid " + BORDER)
                    .set("color", MUTED)
                    .set("font-size", "18px");
            ordersArea.add(empty);
            return;
        }

        for (FoodOrder order : orders) {
            ordersArea.add(buildOrderCard(order));
        }
    }

    private Div buildOrderCard(FoodOrder order) {
        Div card = new Div();
        card.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1.2fr 1fr 1.3fr auto")
                .set("gap", "18px")
                .set("align-items", "center")
                .set("background", PANEL)
                .set("border", "1px solid " + BORDER)
                .set("box-shadow", "0 18px 45px rgba(0,0,0,0.25)")
                .set("padding", "20px")
                .set("box-sizing", "border-box");

        Div booking = new Div();
        H2 ref = new H2(order.getBooking().getBookingReference());
        ref.getStyle()
                .set("font-size", "22px")
                .set("font-weight", "950")
                .set("margin", "0 0 8px 0")
                .set("color", "white");

        Span film = muted(order.getBooking().getScreening().getFilm().getTitle());
        Span when = muted(order.getBooking().getScreening().getScreeningDate() + " " + order.getBooking().getScreening().getStartTime());
        booking.add(ref, film, when);

        Div location = new Div();
        location.add(
                strong("Screen " + order.getBooking().getScreening().getScreen().getScreenNumber()),
                muted(order.getBooking().getScreening().getScreen().getCinema().getName()),
                muted("Seats: " + foodOrderService.findSeatNumbersForBooking(order.getBooking().getId()))
        );

        Div items = new Div();
        items.add(strong(itemText(order)));
        items.add(muted("Delivery: " + order.getDeliveryMethod().getLabel()));
        items.add(muted("Total: " + NumberFormat.getCurrencyInstance(Locale.UK).format(order.getTotalCost())));
        items.add(muted("Ordered: " + order.getOrderTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));

        Div actions = new Div();
        actions.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "8px")
                .set("min-width", "150px");

        Span status = statusBadge(order.getStatus());
        actions.add(status);

        if (order.getStatus() == FoodOrderStatus.PENDING) {
            actions.add(actionButton("Preparing", () -> {
                foodOrderService.markPreparing(order.getId());
                Notification.show("Food order marked as preparing.");
                reloadOrders();
            }));
        }

        if (order.getStatus() == FoodOrderStatus.PENDING || order.getStatus() == FoodOrderStatus.PREPARING) {
            actions.add(actionButton("Delivered", () -> {
                foodOrderService.markDelivered(order.getId());
                Notification.show("Food order marked as delivered.");
                reloadOrders();
            }));
            actions.add(dangerButton("Cancel", () -> {
                foodOrderService.cancelOrder(order.getId());
                Notification.show("Food order cancelled.");
                reloadOrders();
            }));
        }

        card.add(booking, location, items, actions);
        return card;
    }

    private Span strong(String text) {
        Span span = new Span(text == null || text.isBlank() ? "-" : text);
        span.getStyle()
                .set("display", "block")
                .set("font-weight", "900")
                .set("font-size", "16px")
                .set("color", "white")
                .set("margin-bottom", "6px")
                .set("white-space", "pre-line");
        return span;
    }

    private Span muted(String text) {
        Span span = new Span(text == null || text.isBlank() ? "-" : text);
        span.getStyle()
                .set("display", "block")
                .set("color", MUTED)
                .set("font-size", "14px")
                .set("font-weight", "700")
                .set("margin-bottom", "5px");
        return span;
    }

    private Span statusBadge(FoodOrderStatus status) {
        Span badge = new Span(status.getLabel());
        String color = switch (status) {
            case PENDING -> "#fbbf24";
            case PREPARING -> "#38bdf8";
            case DELIVERED -> "#22c55e";
            case CANCELLED -> "#94a3b8";
        };

        badge.getStyle()
                .set("display", "inline-block")
                .set("text-align", "center")
                .set("color", color)
                .set("border", "1px solid " + color)
                .set("padding", "7px 10px")
                .set("font-size", "12px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.08em");
        return badge;
    }

    private Button actionButton(String text, Runnable action) {
        Button button = new Button(text, event -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.getStyle()
                .set("height", "36px")
                .set("background", BLUE)
                .set("border-radius", "0")
                .set("font-weight", "850");
        return button;
    }

    private Button dangerButton(String text, Runnable action) {
        Button button = new Button(text, event -> action.run());
        button.getStyle()
                .set("height", "36px")
                .set("background", "#dc2626")
                .set("color", "white")
                .set("border-radius", "0")
                .set("font-weight", "850");
        return button;
    }

    private String itemText(FoodOrder order) {
        return order.getItems().stream()
                .map(item -> item.getFoodItem().getName() + " x" + item.getQuantity())
                .collect(Collectors.joining("\n"));
    }

}