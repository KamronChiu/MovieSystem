package com.eduaccess.ui;

import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.HallType;
import com.eduaccess.domain.Screen;
import com.eduaccess.service.CinemaService;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@CssImport("./styles/manager-admin-pro.css")
@Route(value = "manager/cinemas", layout = MainLayout.class)
@PageTitle("HCBS — Manager Cinemas")
public class ManagerCinemaView extends Div implements BeforeEnterObserver {

    private final LoginService loginService;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PermissionChecker.checkManagerAccess(event, loginService);
    }

    private final CinemaService cinemaService;

    private final TextField cityField = new TextField("City");
    private final TextField nameField = new TextField("Cinema name");
    private final TextField addressField = new TextField("Address");
    private final IntegerField numberOfScreensField = new IntegerField("Number of screens");
    private final Div capacityFields = new Div();
    private final Grid<Cinema> cinemaGrid = new Grid<>(Cinema.class, false);

    private final List<IntegerField> screenCapacityFields = new ArrayList<>();

    public ManagerCinemaView(CinemaService cinemaService, LoginService loginService) {
        this.cinemaService = cinemaService;
        this.loginService = loginService;

        setWidthFull();
        addClassNames("manager-pro-page", "manager-cinema-page");
        getStyle()
                .set("min-height", "100vh")
                .set("background", "#020b1d")
                .set("color", "white")
                .set("padding", "44px 48px 90px 48px")
                .set("box-sizing", "border-box");

        Div page = new Div();
        page.addClassName("manager-pro-shell");
        page.getStyle()
                .set("max-width", "1320px")
                .set("margin", "0 auto");

        H1 title = new H1("Manager Cinema Management");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "42px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.03em")
                .set("text-transform", "uppercase");

        Paragraph intro = new Paragraph("Add new Horizon Cinemas in existing or new cities. Each new cinema becomes available for film scheduling and customer booking.");
        intro.getStyle()
                .set("max-width", "820px")
                .set("color", "rgba(255,255,255,0.72)")
                .set("font-size", "16px")
                .set("line-height", "1.7")
                .set("margin", "10px 0 34px 0");

        configureFields();
        configureGrid();

        Div layout = new Div();
        layout.addClassNames("manager-pro-layout", "manager-cinema-layout");
        layout.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "420px 1fr")
                .set("gap", "28px")
                .set("align-items", "stretch");
        layout.add(buildFormCard(), buildGridCard());

        page.add(title, intro, layout);
        add(page);

        refreshGrid();
    }

    /**
     * Configure form fields with placeholders and validation
     * 配置表单字段，包括占位符和验证
     * 
     * Key points / 关键点:
     * - numberOfScreensField: 1-6 screens allowed / 允许1-6个影厅
     * - Value change listener triggers rebuildCapacityFields()
     * - 值变化监听器触发生成座位数输入框
     */
    private void configureFields() {
        cityField.setPlaceholder("e.g. Manchester");
        nameField.setPlaceholder("e.g. Horizon Manchester Central");
        addressField.setPlaceholder("e.g. 18 Oxford Road, Manchester");

        // Set screen count range (1-6)
        // 设置影厅数量范围（1-6个）
        numberOfScreensField.setMin(1);
        numberOfScreensField.setMax(6);
        numberOfScreensField.setValue(2);
        numberOfScreensField.setStepButtonsVisible(true);
        
        // Auto-rebuild capacity fields when screen count changes
        // 影厅数量变化时自动重建座位数输入框
        numberOfScreensField.addValueChangeListener(event -> rebuildCapacityFields());

        cityField.setWidthFull();
        nameField.setWidthFull();
        addressField.setWidthFull();
        numberOfScreensField.setWidthFull();

        styleDarkField(cityField);
        styleDarkField(nameField);
        styleDarkField(addressField);
        styleDarkField(numberOfScreensField);

        rebuildCapacityFields();
    }

    private Div buildFormCard() {
        Div card = darkCard();

        H2 heading = new H2("Add cinema");
        heading.getStyle()
                .set("margin", "0 0 20px 0")
                .set("font-size", "24px")
                .set("font-weight", "900");

        Button saveButton = primaryButton("Create cinema");
        saveButton.addClickListener(event -> createCinema());

        Button resetButton = secondaryButton("Reset");
        resetButton.addClickListener(event -> clearForm());

        HorizontalLayout buttons = new HorizontalLayout(saveButton, resetButton);
        buttons.setSpacing(true);
        buttons.getStyle().set("margin-top", "20px");

        card.add(heading, cityField, nameField, addressField, numberOfScreensField, capacityFields, buttons);
        return card;
    }

    private Div buildGridCard() {
        Div card = darkCard();
        // Make the grid card a flex column so the inner Grid can stretch and
        // match the form card's height (CSS Grid layout's align-items:stretch).
        // Do NOT set height: 100% explicitly - that would override stretch.
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("min-height", "0")
                .set("overflow", "hidden");

        H2 heading = new H2("Existing cinemas");
        heading.getStyle()
                .set("margin", "0 0 20px 0")
                .set("font-size", "24px")
                .set("font-weight", "900")
                .set("flex", "0 0 auto");

        // Wrap the grid so it can flex-grow within the card.
        Div gridWrapper = new Div(cinemaGrid);
        gridWrapper.getStyle()
                .set("flex", "1 1 0")
                .set("min-height", "0")
                .set("display", "flex");

        card.add(heading, gridWrapper);
        return card;
    }

    private void configureGrid() {
        cinemaGrid.addColumn(Cinema::getCity)
                .setHeader("City")
                .setAutoWidth(true)
                .setSortable(true);

        cinemaGrid.addColumn(Cinema::getName)
                .setHeader("Cinema")
                .setAutoWidth(true)
                .setSortable(true);

        cinemaGrid.addColumn(Cinema::getAddress)
                .setHeader("Address")
                .setAutoWidth(true);

        cinemaGrid.addColumn(cinema -> cinemaService.findScreensForCinema(cinema.getId()).size())
                .setHeader("Screens")
                .setAutoWidth(true);

        cinemaGrid.addColumn(cinema -> cinemaService.findScreensForCinema(cinema.getId())
                        .stream()
                        .mapToInt(Screen::getCapacity)
                        .sum())
                .setHeader("Total seats")
                .setAutoWidth(true);

        cinemaGrid.addColumn(cinema -> cinemaService.findScreensForCinema(cinema.getId())
                        .stream()
                        .map(screen -> screen.getHallType().getLabel())
                        .distinct()
                        .collect(Collectors.joining(" / ")))
                .setHeader("Hall Types")
                .setAutoWidth(true);

        cinemaGrid.addComponentColumn(cinema -> {
            IntegerField capacity = new IntegerField();
            capacity.setPlaceholder("30–120");
            capacity.setMin(30);
            capacity.setMax(120);
            capacity.setWidth("110px");
            styleDarkField(capacity);

            ComboBox<HallType> hallTypeSelect = new ComboBox<>();
            hallTypeSelect.setItems(HallType.values());
            hallTypeSelect.setItemLabelGenerator(HallType::getLabel);
            hallTypeSelect.setValue(HallType.REGULAR);
            hallTypeSelect.setWidth("110px");
            styleDarkField(hallTypeSelect);

            Button addScreen = secondaryButton("Add screen");
            addScreen.addClickListener(event -> {
                try {
                    cinemaService.addScreen(cinema.getId(), capacity.getValue(), hallTypeSelect.getValue());
                    Notification.show("Screen added to " + cinema.getName());
                    refreshGrid();
                } catch (RuntimeException ex) {
                    Notification.show(ex.getMessage());
                }
            });

            HorizontalLayout row = new HorizontalLayout(capacity, hallTypeSelect, addScreen);
            row.setAlignItems(FlexComponent.Alignment.END);
            return row;
        }).setHeader("Expansion");

        cinemaGrid.setSizeFull();
        cinemaGrid.addClassName("manager-pro-grid");
        cinemaGrid.getStyle()
                .set("background", "transparent")
                .set("border-radius", "14px")
                .set("overflow", "hidden");
    }

    /**
     * Rebuild capacity input fields dynamically based on number of screens
     * 根据影厅数量动态重建座位数输入字段
     * 
     * Example / 示例:
     * - User selects 3 screens → generate 3 input fields
     * - 用户选择3个影厅 → 生成3个输入框
     */
    private void rebuildCapacityFields() {
        capacityFields.removeAll();
        screenCapacityFields.clear();

        Integer count = numberOfScreensField.getValue();
        int screenCount = count == null ? 1 : Math.max(1, Math.min(6, count));

        // Create grid layout for capacity inputs
        // 为座位数输入创建网格布局
        Span label = new Span("Screen capacities");
        label.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("font-weight", "800")
                .set("color", "rgba(255,255,255,0.82)")
                .set("margin", "18px 0 8px 0");

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(2, minmax(0, 1fr))")
                .set("gap", "12px");

        // Generate input field for each screen
        // 为每个影厅生成一个输入字段
        for (int i = 1; i <= screenCount; i++) {
            IntegerField field = new IntegerField("Screen " + i);
            field.setPlaceholder("50–120");
            field.setValue(i % 2 == 0 ? 90 : 70); // Default capacity / 默认座位数
            field.setMin(50);
            field.setMax(120);
            field.setStepButtonsVisible(true);
            field.setWidthFull();
            styleDarkField(field);
            screenCapacityFields.add(field);
            grid.add(field);
        }

        capacityFields.add(label, grid);
    }

    /**
     * Create new cinema with screens and seats
     * 创建新影院，包括影厅和座位
     * 
     * Flow / 流程:
     * 1. Get screen capacities from form fields / 从表单获取每个影厅的座位数
     * 2. Call CinemaService.createCinema() / 调用服务层创建影院
     * 3. Service auto-generates screens and seats / 服务层自动生成影厅和座位
     * 4. Refresh grid and show success message / 刷新列表并显示成功消息
     */
    private void createCinema() {
        try {
            // Collect screen capacities from dynamic form fields
            // 从动态表单字段收集每个影厅的座位数
            List<Integer> capacities = screenCapacityFields.stream()
                    .map(IntegerField::getValue)
                    .toList();

            // Call service layer to create cinema
            // 调用服务层创建影院（包含城市、名称、地址、影厅座位数）
            Cinema createdCinema = cinemaService.createCinema(
                    cityField.getValue(),
                    nameField.getValue(),
                    addressField.getValue(),
                    capacities
            );

            // Clear form and refresh grid
            // 清空表单并刷新影院列表
            Notification.show("✅ Cinema created successfully: " + createdCinema.getName());
            clearForm();
            refreshGrid();
            
            // 显示创建的影院详情
            long screenCount = cinemaService.findScreensForCinema(createdCinema.getId()).size();
            int totalSeats = cinemaService.findScreensForCinema(createdCinema.getId())
                    .stream()
                    .mapToInt(Screen::getCapacity)
                    .sum();
            
            Notification.show(
                    "🎬 " + createdCinema.getName() + "\n" +
                    "📍 " + createdCinema.getCity() + "\n" +
                    "🎥 " + screenCount + " screens | " +
                    "💺 " + totalSeats + " total seats",
                    5000, Notification.Position.TOP_CENTER
            );
            
        } catch (RuntimeException ex) {
            Notification.show("❌ " + ex.getMessage());
        }
    }

    private void clearForm() {
        cityField.clear();
        nameField.clear();
        addressField.clear();
        numberOfScreensField.setValue(2);
        rebuildCapacityFields();
    }

    /**
     * Refresh grid with latest cinema data from database
     * 从数据库加载最新影院数据并刷新列表
     * 
     * Flow / 流程:
     * 1. Call cinemaService.findAllCinemas() to get all cinemas
     * 2. Set the list as grid items
     * 3. Grid automatically re-renders with new data
     *
     * Called after create/delete operations
     * 在创建/删除操作后调用
     */
    private void refreshGrid() {
        cinemaGrid.setItems(cinemaService.findAllCinemas());
    }

    private Div darkCard() {
        Div card = new Div();
        card.addClassName("manager-pro-card");
        card.getStyle()
                .set("background", "rgba(255,255,255,0.055)")
                .set("border", "1px solid rgba(255,255,255,0.13)")
                .set("box-shadow", "0 22px 70px rgba(0,0,0,0.32)")
                .set("padding", "24px")
                .set("box-sizing", "border-box");
        return card;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.getStyle()
                .set("height", "46px")
                .set("background", "#0072ce")
                .set("border-radius", "0")
                .set("font-weight", "900")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)")
                .set("padding", "0 36px 0 30px");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyle()
                .set("height", "42px")
                .set("background", "transparent")
                .set("color", "white")
                .set("border", "1px solid rgba(255,255,255,0.45)")
                .set("border-radius", "0")
                .set("font-weight", "800");
        return button;
    }

    private void styleDarkField(com.vaadin.flow.component.Component component) {
        component.getElement().getStyle()
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.08)")
                .set("--vaadin-input-field-value-color", "white")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.82)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.50)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.20)")
                .set("--vaadin-input-field-focused-highlight", "#38bdf8")
                .set("color", "white");
    }
}
