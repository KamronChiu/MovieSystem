package com.eduaccess.service;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.FoodOrder;
import com.eduaccess.domain.FoodOrderStatus;
import com.eduaccess.domain.ManagerFeedback;
import com.eduaccess.domain.UserAccount;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.FoodOrderRepository;
import com.eduaccess.repository.ManagerFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ManagerDashboardService {

    private final BookingRepository bookingRepository;
    private final FoodOrderRepository foodOrderRepository;
    private final ManagerFeedbackRepository feedbackRepository;
    private final LoginService loginService;
    private final AuditLogService auditLogService;

    public ManagerDashboardService(
            BookingRepository bookingRepository,
            FoodOrderRepository foodOrderRepository,
            ManagerFeedbackRepository feedbackRepository,
            LoginService loginService,
            AuditLogService auditLogService
    ) {
        this.bookingRepository = bookingRepository;
        this.foodOrderRepository = foodOrderRepository;
        this.feedbackRepository = feedbackRepository;
        this.loginService = loginService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public DashboardData buildDashboard(LocalDate startDate, LocalDate endDate) {
        LocalDate calculatedStart = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate calculatedEnd = endDate == null ? LocalDate.now() : endDate;

        if (calculatedStart.isAfter(calculatedEnd)) {
            LocalDate temp = calculatedStart;
            calculatedStart = calculatedEnd;
            calculatedEnd = temp;
        }

        final LocalDate start = calculatedStart;
        final LocalDate end = calculatedEnd;

        List<Booking> allBookings = bookingRepository.findAllByOrderByBookingDateDesc();
        List<FoodOrder> allFoodOrders = foodOrderRepository.findAllByOrderByOrderTimeDesc();

        List<Booking> bookings = allBookings.stream()
                .filter(booking -> isBetween(booking.getBookingDate(), start, end))
                .toList();

        List<FoodOrder> foodOrders = allFoodOrders.stream()
                .filter(order -> isBetween(order.getOrderTime(), start, end))
                .toList();

        List<Booking> confirmedBookings = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .toList();

        List<FoodOrder> activeFoodOrders = foodOrders.stream()
                .filter(order -> order.getStatus() != FoodOrderStatus.CANCELLED)
                .toList();

        long ticketsSold = confirmedBookings.stream()
                .mapToLong(this::seatCount)
                .sum();

        BigDecimal ticketRevenue = confirmedBookings.stream()
                .map(Booking::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal foodRevenue = activeFoodOrders.stream()
                .map(FoodOrder::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long cancelledBookings = bookings.stream()
                .filter(this::isCancellationRelated)
                .count();

        long periodLength = Math.max(1, end.toEpochDay() - start.toEpochDay() + 1);
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(periodLength - 1);
        BigDecimal previousGrandRevenue = calculateGrandRevenueForPeriod(allBookings, allFoodOrders, previousStart, previousEnd);
        BigDecimal grandRevenue = ticketRevenue.add(foodRevenue);
        BigDecimal revenueChangePercent = percentageChange(grandRevenue, previousGrandRevenue);

        long activeFoodOrderCount = activeFoodOrders.size();
        BigDecimal foodAttachRate = confirmedBookings.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(activeFoodOrderCount * 100.0 / confirmedBookings.size()).setScale(1, RoundingMode.HALF_UP);
        long totalBookings = bookings.size();
        BigDecimal cancelRate = totalBookings == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancelledBookings * 100.0 / totalBookings).setScale(1, RoundingMode.HALF_UP);

        List<DailyRevenueRow> dailyRevenue = dailyRevenueRows(confirmedBookings, activeFoodOrders, start, end);
        List<FilmSalesRow> filmSales = filmSalesRows(confirmedBookings);
        List<CinemaRevenueRow> cinemaRevenue = cinemaRevenueRows(confirmedBookings);
        List<StatusRow> bookingStatus = statusRows(bookings);
        List<ShowtimeHeatmapRow> heatmap = showtimeHeatmapRows(confirmedBookings);

        Summary summary = new Summary(
                confirmedBookings.size(),
                ticketsSold,
                cancelledBookings,
                activeFoodOrderCount,
                ticketRevenue,
                foodRevenue,
                grandRevenue,
                previousGrandRevenue,
                revenueChangePercent,
                foodAttachRate,
                cancelRate
        );

        List<ExecutiveBriefRow> executiveBrief = executiveBriefRows(summary, filmSales, cinemaRevenue, heatmap);
        List<DecisionActionRow> decisionActions = decisionActionRows(summary, filmSales, cinemaRevenue, heatmap);

        return new DashboardData(
                start,
                end,
                summary,
                dailyRevenue,
                filmSales,
                cinemaRevenue,
                bookingStatus,
                heatmap,
                executiveBrief,
                decisionActions,
                feedbackRepository.findTop20ByOrderByCreatedAtDesc()
        );
    }

    @Transactional
    public ManagerFeedback saveFeedback(String title, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Feedback comment is required.");
        }

        UserAccount user = null;
        try {
            user = loginService.getCurrentUser();
        } catch (RuntimeException ignored) {
        }

        ManagerFeedback feedback = new ManagerFeedback(
                user == null ? "manager" : user.getUsername(),
                user == null ? "Manager" : user.getFullName(),
                title,
                comment.trim()
        );

        ManagerFeedback saved = feedbackRepository.save(feedback);
        auditLogService.record(
                com.eduaccess.domain.AuditAction.FEEDBACK_CREATED,
                "ManagerFeedback",
                saved.getId(),
                null,
                null,
                null,
                null,
                "Manager feedback saved",
                safe(saved.getTitle()) + ": " + safe(saved.getComment())
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public String exportCsv(LocalDate startDate, LocalDate endDate) {
        DashboardData data = buildDashboard(startDate, endDate);
        StringBuilder csv = new StringBuilder();

        csv.append("HCBS Manager Dashboard Export\n");
        csv.append("Date range,").append(data.startDate()).append(" to ").append(data.endDate()).append("\n\n");

        csv.append("Summary\n");
        csv.append("Confirmed bookings,").append(data.summary().confirmedBookings()).append("\n");
        csv.append("Tickets sold,").append(data.summary().ticketsSold()).append("\n");
        csv.append("Cancelled / refund-related bookings,").append(data.summary().cancelledBookings()).append("\n");
        csv.append("Food orders,").append(data.summary().foodOrders()).append("\n");
        csv.append("Ticket revenue,").append(data.summary().ticketRevenue()).append("\n");
        csv.append("Food revenue,").append(data.summary().foodRevenue()).append("\n");
        csv.append("Grand revenue,").append(data.summary().grandRevenue()).append("\n");
        csv.append("Previous period revenue,").append(data.summary().previousGrandRevenue()).append("\n");
        csv.append("Revenue change %,").append(data.summary().revenueChangePercent()).append("\n");
        csv.append("Food attach rate %,").append(data.summary().foodAttachRate()).append("\n");
        csv.append("Cancel rate %,").append(data.summary().cancelRate()).append("\n\n");

        csv.append("Executive brief\nMetric,Value,Tone\n");
        for (ExecutiveBriefRow row : data.executiveBrief()) {
            csv.append(escape(row.label())).append(',')
                    .append(escape(row.value())).append(',')
                    .append(escape(row.tone())).append('\n');
        }

        csv.append("\nRecommended actions\nPriority,Title,Recommendation,Tone\n");
        for (DecisionActionRow row : data.decisionActions()) {
            csv.append(escape(row.priority())).append(',')
                    .append(escape(row.title())).append(',')
                    .append(escape(row.description())).append(',')
                    .append(escape(row.tone())).append('\n');
        }

        csv.append("\nDaily revenue\nDate,Ticket Revenue,Food Revenue,Total Revenue\n");
        for (DailyRevenueRow row : data.dailyRevenue()) {
            csv.append(row.date()).append(',')
                    .append(row.ticketRevenue()).append(',')
                    .append(row.foodRevenue()).append(',')
                    .append(row.totalRevenue()).append('\n');
        }

        csv.append("\nShowtime heatmap\nDay,Slot,Tickets,Revenue\n");
        for (ShowtimeHeatmapRow row : data.showtimeHeatmap()) {
            for (ShowtimeHeatmapCell cell : row.cells()) {
                csv.append(row.day()).append(',')
                        .append(cell.slot()).append(',')
                        .append(cell.ticketsSold()).append(',')
                        .append(cell.revenue()).append('\n');
            }
        }

        csv.append("\nFilm sales\nFilm,Tickets,Revenue\n");
        for (FilmSalesRow row : data.filmSales()) {
            csv.append(escape(row.filmTitle())).append(',')
                    .append(row.ticketsSold()).append(',')
                    .append(row.revenue()).append('\n');
        }

        csv.append("\nCinema revenue\nCinema,Tickets,Revenue\n");
        for (CinemaRevenueRow row : data.cinemaRevenue()) {
            csv.append(escape(row.cinemaName())).append(',')
                    .append(row.ticketsSold()).append(',')
                    .append(row.revenue()).append('\n');
        }

        csv.append("\nBooking status\nStatus,Count\n");
        for (StatusRow row : data.bookingStatus()) {
            csv.append(row.status()).append(',').append(row.count()).append('\n');
        }

        csv.append("\nManager feedback\nCreated At,Manager,Title,Comment\n");
        for (ManagerFeedback feedback : data.recentFeedback()) {
            String createdAt = feedback.getCreatedAt() == null
                    ? ""
                    : feedback.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            csv.append(createdAt).append(',')
                    .append(escape(feedback.getManagerName())).append(',')
                    .append(escape(feedback.getTitle())).append(',')
                    .append(escape(feedback.getComment())).append('\n');
        }

        return csv.toString();
    }

    private BigDecimal calculateGrandRevenueForPeriod(
            List<Booking> allBookings,
            List<FoodOrder> allFoodOrders,
            LocalDate start,
            LocalDate end
    ) {
        BigDecimal tickets = allBookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .filter(booking -> isBetween(booking.getBookingDate(), start, end))
                .map(Booking::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal food = allFoodOrders.stream()
                .filter(order -> order.getStatus() != FoodOrderStatus.CANCELLED)
                .filter(order -> isBetween(order.getOrderTime(), start, end))
                .map(FoodOrder::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return tickets.add(food);
    }

    private BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current == null || current.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(100);
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private boolean isBetween(LocalDateTime value, LocalDate start, LocalDate end) {
        if (value == null) {
            return false;
        }
        LocalDate date = value.toLocalDate();
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private boolean isCancellationRelated(Booking booking) {
        return booking != null
                && booking.getStatus() != null
                && booking.getStatus().name().contains("CANCEL");
    }

    private long seatCount(Booking booking) {
        return booking == null || booking.getBookingSeats() == null ? 0 : booking.getBookingSeats().size();
    }

    private BigDecimal bookingRevenue(Booking booking) {
        return booking == null || booking.getTotalCost() == null ? BigDecimal.ZERO : booking.getTotalCost();
    }

    private List<DailyRevenueRow> dailyRevenueRows(
            List<Booking> confirmedBookings,
            List<FoodOrder> foodOrders,
            LocalDate start,
            LocalDate end
    ) {
        Map<LocalDate, BigDecimal> ticketRevenueByDate = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> foodRevenueByDate = new LinkedHashMap<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            ticketRevenueByDate.put(date, BigDecimal.ZERO);
            foodRevenueByDate.put(date, BigDecimal.ZERO);
        }

        for (Booking booking : confirmedBookings) {
            if (booking.getBookingDate() == null) {
                continue;
            }
            LocalDate date = booking.getBookingDate().toLocalDate();
            ticketRevenueByDate.computeIfPresent(date, (key, value) -> value.add(bookingRevenue(booking)));
        }

        for (FoodOrder order : foodOrders) {
            if (order.getOrderTime() == null) {
                continue;
            }
            LocalDate date = order.getOrderTime().toLocalDate();
            foodRevenueByDate.computeIfPresent(
                    date,
                    (key, value) -> value.add(order.getTotalCost() == null ? BigDecimal.ZERO : order.getTotalCost())
            );
        }

        List<DailyRevenueRow> rows = new ArrayList<>();
        for (LocalDate date : ticketRevenueByDate.keySet()) {
            BigDecimal ticket = ticketRevenueByDate.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal food = foodRevenueByDate.getOrDefault(date, BigDecimal.ZERO);
            rows.add(new DailyRevenueRow(date, ticket, food, ticket.add(food)));
        }
        return rows;
    }

    private List<FilmSalesRow> filmSalesRows(List<Booking> confirmedBookings) {
        Map<String, FilmAccumulator> byFilm = new LinkedHashMap<>();

        for (Booking booking : confirmedBookings) {
            if (booking.getScreening() == null || booking.getScreening().getFilm() == null) {
                continue;
            }
            String title = safe(booking.getScreening().getFilm().getTitle());
            FilmAccumulator acc = byFilm.computeIfAbsent(title, key -> new FilmAccumulator());
            acc.tickets += seatCount(booking);
            acc.revenue = acc.revenue.add(bookingRevenue(booking));
        }

        return byFilm.entrySet()
                .stream()
                .map(entry -> new FilmSalesRow(entry.getKey(), entry.getValue().tickets, entry.getValue().revenue))
                .sorted(Comparator.comparing(FilmSalesRow::revenue).reversed())
                .toList();
    }

    private List<CinemaRevenueRow> cinemaRevenueRows(List<Booking> confirmedBookings) {
        Map<String, FilmAccumulator> byCinema = new LinkedHashMap<>();

        for (Booking booking : confirmedBookings) {
            if (booking.getScreening() == null
                    || booking.getScreening().getScreen() == null
                    || booking.getScreening().getScreen().getCinema() == null) {
                continue;
            }
            String cinema = safe(booking.getScreening().getScreen().getCinema().getName());
            FilmAccumulator acc = byCinema.computeIfAbsent(cinema, key -> new FilmAccumulator());
            acc.tickets += seatCount(booking);
            acc.revenue = acc.revenue.add(bookingRevenue(booking));
        }

        return byCinema.entrySet()
                .stream()
                .map(entry -> new CinemaRevenueRow(entry.getKey(), entry.getValue().tickets, entry.getValue().revenue))
                .sorted(Comparator.comparing(CinemaRevenueRow::revenue).reversed())
                .toList();
    }

    private List<StatusRow> statusRows(List<Booking> bookings) {
        return bookings.stream()
                .collect(Collectors.groupingBy(
                        booking -> booking.getStatus() == null ? "UNKNOWN" : booking.getStatus().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .map(entry -> new StatusRow(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ShowtimeHeatmapRow> showtimeHeatmapRows(List<Booking> confirmedBookings) {
        Map<DayOfWeek, Map<ShowtimeSlot, HeatAccumulator>> heatmap = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            Map<ShowtimeSlot, HeatAccumulator> slotMap = new EnumMap<>(ShowtimeSlot.class);
            for (ShowtimeSlot slot : ShowtimeSlot.values()) {
                slotMap.put(slot, new HeatAccumulator());
            }
            heatmap.put(day, slotMap);
        }

        for (Booking booking : confirmedBookings) {
            if (booking.getScreening() == null
                    || booking.getScreening().getScreeningDate() == null
                    || booking.getScreening().getStartTime() == null) {
                continue;
            }
            DayOfWeek day = booking.getScreening().getScreeningDate().getDayOfWeek();
            ShowtimeSlot slot = ShowtimeSlot.from(booking.getScreening().getStartTime());
            HeatAccumulator acc = heatmap.get(day).get(slot);
            acc.tickets += seatCount(booking);
            acc.revenue = acc.revenue.add(bookingRevenue(booking));
        }

        List<ShowtimeHeatmapRow> rows = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            List<ShowtimeHeatmapCell> cells = new ArrayList<>();
            for (ShowtimeSlot slot : ShowtimeSlot.values()) {
                HeatAccumulator acc = heatmap.get(day).get(slot);
                cells.add(new ShowtimeHeatmapCell(slot.label, acc.tickets, acc.revenue));
            }
            rows.add(new ShowtimeHeatmapRow(dayLabel(day), cells));
        }
        return rows;
    }

    private List<ExecutiveBriefRow> executiveBriefRows(
            Summary summary,
            List<FilmSalesRow> filmSales,
            List<CinemaRevenueRow> cinemaRevenue,
            List<ShowtimeHeatmapRow> heatmap
    ) {
        List<ExecutiveBriefRow> rows = new ArrayList<>();
        String growthTone = summary.revenueChangePercent().compareTo(BigDecimal.ZERO) >= 0 ? "positive" : "warning";
        rows.add(new ExecutiveBriefRow(
                "Revenue movement",
                signedPercent(summary.revenueChangePercent()) + " vs previous period",
                growthTone
        ));

        rows.add(new ExecutiveBriefRow(
                "Top film",
                filmSales.isEmpty() ? "No confirmed film sales yet" : filmSales.getFirst().filmTitle(),
                filmSales.isEmpty() ? "warning" : "positive"
        ));

        rows.add(new ExecutiveBriefRow(
                "Best cinema",
                cinemaRevenue.isEmpty() ? "No cinema revenue yet" : cinemaRevenue.getFirst().cinemaName(),
                cinemaRevenue.isEmpty() ? "warning" : "positive"
        ));

        rows.add(new ExecutiveBriefRow(
                "Food attach rate",
                summary.foodAttachRate() + "% of confirmed bookings",
                summary.foodAttachRate().compareTo(BigDecimal.valueOf(50)) >= 0 ? "positive" : "neutral"
        ));

        ShowtimePeak peak = findPeakHeatmapCell(heatmap);
        rows.add(new ExecutiveBriefRow(
                "Peak showtime",
                peak.tickets() == 0 ? "No clear peak yet" : peak.day() + " · " + peak.slot(),
                peak.tickets() == 0 ? "neutral" : "positive"
        ));

        rows.add(new ExecutiveBriefRow(
                "Risk signal",
                summary.cancelRate().compareTo(BigDecimal.valueOf(20)) > 0
                        ? "Cancellation rate needs review"
                        : "Cancellation level is controlled",
                summary.cancelRate().compareTo(BigDecimal.valueOf(20)) > 0 ? "warning" : "positive"
        ));

        return rows;
    }

    private List<DecisionActionRow> decisionActionRows(
            Summary summary,
            List<FilmSalesRow> filmSales,
            List<CinemaRevenueRow> cinemaRevenue,
            List<ShowtimeHeatmapRow> heatmap
    ) {
        List<DecisionActionRow> actions = new ArrayList<>();

        if (summary.grandRevenue().compareTo(BigDecimal.ZERO) == 0) {
            actions.add(new DecisionActionRow(
                    "High",
                    "Check active screenings",
                    "No revenue appears in this period. Confirm that films have future screenings and booking staff are using the correct date range.",
                    "warning"
            ));
        } else if (!filmSales.isEmpty()) {
            FilmSalesRow topFilm = filmSales.getFirst();
            actions.add(new DecisionActionRow(
                    "High",
                    "Protect demand for " + topFilm.filmTitle(),
                    "Top film generated " + topFilm.ticketsSold() + " sold seats. Review evening and weekend capacity before demand peaks.",
                    "positive"
            ));
        }

        ShowtimePeak peak = findPeakHeatmapCell(heatmap);
        if (peak.tickets() > 0) {
            actions.add(new DecisionActionRow(
                    "Medium",
                    "Use heatmap peak slots",
                    peak.day() + " " + peak.slot() + " is currently the strongest slot. Consider adding premium screenings around this period.",
                    "neutral"
            ));
        }

        if (summary.foodAttachRate().compareTo(BigDecimal.valueOf(35)) < 0 && summary.confirmedBookings() > 0) {
            actions.add(new DecisionActionRow(
                    "Medium",
                    "Increase food conversion",
                    "Food attach rate is " + summary.foodAttachRate() + "%. Promote combo meals during checkout and before evening shows.",
                    "warning"
            ));
        } else if (summary.foodRevenue().compareTo(BigDecimal.ZERO) > 0) {
            actions.add(new DecisionActionRow(
                    "Low",
                    "Maintain concession workflow",
                    "Food revenue is active. Keep Food Orders visible for staff and monitor delivery-to-seat performance.",
                    "positive"
            ));
        }

        if (summary.cancelRate().compareTo(BigDecimal.valueOf(20)) > 0) {
            actions.add(new DecisionActionRow(
                    "High",
                    "Review cancellation pressure",
                    "Cancellation rate is " + summary.cancelRate() + "%. Check cancellation reasons, film timings and customer follow-up.",
                    "warning"
            ));
        }

        if (cinemaRevenue.size() >= 2) {
            CinemaRevenueRow top = cinemaRevenue.getFirst();
            CinemaRevenueRow low = cinemaRevenue.get(cinemaRevenue.size() - 1);
            if (top.revenue().compareTo(BigDecimal.ZERO) > 0
                    && low.revenue().multiply(BigDecimal.valueOf(2)).compareTo(top.revenue()) < 0) {
                actions.add(new DecisionActionRow(
                        "Low",
                        "Balance cinema performance",
                        low.cinemaName() + " is behind the leading cinema. Review local film mix, showtimes and staff promotion.",
                        "neutral"
                ));
            }
        }

        if (actions.isEmpty()) {
            actions.add(new DecisionActionRow(
                    "Low",
                    "Keep monitoring",
                    "Current operations look stable. Continue tracking revenue, food conversion and customer cancellation trends.",
                    "positive"
            ));
        }

        return actions.stream().limit(4).toList();
    }

    private ShowtimePeak findPeakHeatmapCell(List<ShowtimeHeatmapRow> heatmap) {
        String bestDay = "";
        String bestSlot = "";
        long bestTickets = 0;
        BigDecimal bestRevenue = BigDecimal.ZERO;
        for (ShowtimeHeatmapRow row : heatmap) {
            for (ShowtimeHeatmapCell cell : row.cells()) {
                if (cell.ticketsSold() > bestTickets
                        || (cell.ticketsSold() == bestTickets && cell.revenue().compareTo(bestRevenue) > 0)) {
                    bestDay = row.day();
                    bestSlot = cell.slot();
                    bestTickets = cell.ticketsSold();
                    bestRevenue = cell.revenue();
                }
            }
        }
        return new ShowtimePeak(bestDay, bestSlot, bestTickets, bestRevenue);
    }

    private String dayLabel(DayOfWeek day) {
        String raw = day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK);
        return raw.substring(0, 1).toUpperCase(Locale.UK) + raw.substring(1);
    }

    private String signedPercent(BigDecimal value) {
        if (value == null) {
            return "0.0%";
        }
        return (value.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + value.setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Unknown" : value.trim();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\n", " ").replace("\r", " ");
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains(" ")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    private enum ShowtimeSlot {
        MORNING("Morning", LocalTime.of(0, 0), LocalTime.of(12, 0)),
        AFTERNOON("Afternoon", LocalTime.of(12, 0), LocalTime.of(17, 0)),
        EVENING("Evening", LocalTime.of(17, 0), LocalTime.of(21, 0)),
        LATE("Late", LocalTime.of(21, 0), LocalTime.MAX);

        private final String label;
        private final LocalTime start;
        private final LocalTime end;

        ShowtimeSlot(String label, LocalTime start, LocalTime end) {
            this.label = label;
            this.start = start;
            this.end = end;
        }

        private static ShowtimeSlot from(LocalTime time) {
            for (ShowtimeSlot slot : values()) {
                if (!time.isBefore(slot.start) && time.isBefore(slot.end)) {
                    return slot;
                }
            }
            return LATE;
        }
    }

    private static class FilmAccumulator {
        private long tickets;
        private BigDecimal revenue = BigDecimal.ZERO;
    }

    private static class HeatAccumulator {
        private long tickets;
        private BigDecimal revenue = BigDecimal.ZERO;
    }

    public record DashboardData(
            LocalDate startDate,
            LocalDate endDate,
            Summary summary,
            List<DailyRevenueRow> dailyRevenue,
            List<FilmSalesRow> filmSales,
            List<CinemaRevenueRow> cinemaRevenue,
            List<StatusRow> bookingStatus,
            List<ShowtimeHeatmapRow> showtimeHeatmap,
            List<ExecutiveBriefRow> executiveBrief,
            List<DecisionActionRow> decisionActions,
            List<ManagerFeedback> recentFeedback
    ) {
    }

    public record Summary(
            long confirmedBookings,
            long ticketsSold,
            long cancelledBookings,
            long foodOrders,
            BigDecimal ticketRevenue,
            BigDecimal foodRevenue,
            BigDecimal grandRevenue,
            BigDecimal previousGrandRevenue,
            BigDecimal revenueChangePercent,
            BigDecimal foodAttachRate,
            BigDecimal cancelRate
    ) {
    }

    public record DailyRevenueRow(
            LocalDate date,
            BigDecimal ticketRevenue,
            BigDecimal foodRevenue,
            BigDecimal totalRevenue
    ) {
    }

    public record FilmSalesRow(String filmTitle, long ticketsSold, BigDecimal revenue) {
    }

    public record CinemaRevenueRow(String cinemaName, long ticketsSold, BigDecimal revenue) {
    }

    public record StatusRow(String status, long count) {
    }

    public record ShowtimeHeatmapRow(String day, List<ShowtimeHeatmapCell> cells) {
    }

    public record ShowtimeHeatmapCell(String slot, long ticketsSold, BigDecimal revenue) {
    }

    public record ExecutiveBriefRow(String label, String value, String tone) {
    }

    public record DecisionActionRow(String priority, String title, String description, String tone) {
    }

    private record ShowtimePeak(String day, String slot, long tickets, BigDecimal revenue) {
    }
}
