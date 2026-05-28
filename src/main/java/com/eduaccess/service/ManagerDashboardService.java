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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;

        List<Booking> bookings = bookingRepository.findAllByOrderByBookingDateDesc()
                .stream()
                .filter(booking -> isBetween(booking.getBookingDate(), start, end))
                .toList();

        List<FoodOrder> foodOrders = foodOrderRepository.findAllByOrderByOrderTimeDesc()
                .stream()
                .filter(order -> isBetween(order.getOrderTime(), start, end))
                .toList();

        List<Booking> confirmedBookings = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .toList();

        long ticketsSold = confirmedBookings.stream()
                .mapToLong(booking -> booking.getBookingSeats() == null ? 0 : booking.getBookingSeats().size())
                .sum();

        BigDecimal ticketRevenue = confirmedBookings.stream()
                .map(Booking::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal foodRevenue = foodOrders.stream()
                .filter(order -> order.getStatus() != FoodOrderStatus.CANCELLED)
                .map(FoodOrder::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long cancelledBookings = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CANCELLED)
                .count();

        Summary summary = new Summary(
                confirmedBookings.size(),
                ticketsSold,
                cancelledBookings,
                ticketRevenue,
                foodRevenue,
                ticketRevenue.add(foodRevenue)
        );

        return new DashboardData(
                start,
                end,
                summary,
                dailyRevenueRows(confirmedBookings, foodOrders, start, end),
                filmSalesRows(confirmedBookings),
                cinemaRevenueRows(confirmedBookings),
                statusRows(bookings),
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
                saved.getTitle() + ": " + saved.getComment()
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
        csv.append("Cancelled bookings,").append(data.summary().cancelledBookings()).append("\n");
        csv.append("Ticket revenue,").append(data.summary().ticketRevenue()).append("\n");
        csv.append("Food revenue,").append(data.summary().foodRevenue()).append("\n");
        csv.append("Grand revenue,").append(data.summary().grandRevenue()).append("\n\n");

        csv.append("Daily revenue\nDate,Ticket Revenue,Food Revenue,Total Revenue\n");
        for (DailyRevenueRow row : data.dailyRevenue()) {
            csv.append(row.date()).append(',')
                    .append(row.ticketRevenue()).append(',')
                    .append(row.foodRevenue()).append(',')
                    .append(row.totalRevenue()).append('\n');
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
            csv.append(feedback.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append(',')
                    .append(escape(feedback.getManagerName())).append(',')
                    .append(escape(feedback.getTitle())).append(',')
                    .append(escape(feedback.getComment())).append('\n');
        }

        return csv.toString();
    }

    private boolean isBetween(LocalDateTime value, LocalDate start, LocalDate end) {
        if (value == null) {
            return false;
        }
        LocalDate date = value.toLocalDate();
        return !date.isBefore(start) && !date.isAfter(end);
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
            LocalDate date = booking.getBookingDate().toLocalDate();
            ticketRevenueByDate.computeIfPresent(
                    date,
                    (key, value) -> value.add(booking.getTotalCost() == null ? BigDecimal.ZERO : booking.getTotalCost())
            );
        }

        for (FoodOrder order : foodOrders) {
            if (order.getStatus() == FoodOrderStatus.CANCELLED || order.getOrderTime() == null) {
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
            String title = booking.getScreening().getFilm().getTitle();
            FilmAccumulator acc = byFilm.computeIfAbsent(title, key -> new FilmAccumulator());
            acc.tickets += booking.getBookingSeats() == null ? 0 : booking.getBookingSeats().size();
            acc.revenue = acc.revenue.add(booking.getTotalCost() == null ? BigDecimal.ZERO : booking.getTotalCost());
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
            String cinema = booking.getScreening().getScreen().getCinema().getName();
            FilmAccumulator acc = byCinema.computeIfAbsent(cinema, key -> new FilmAccumulator());
            acc.tickets += booking.getBookingSeats() == null ? 0 : booking.getBookingSeats().size();
            acc.revenue = acc.revenue.add(booking.getTotalCost() == null ? BigDecimal.ZERO : booking.getTotalCost());
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

    private static class FilmAccumulator {
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
            List<ManagerFeedback> recentFeedback
    ) {
    }

    public record Summary(
            long confirmedBookings,
            long ticketsSold,
            long cancelledBookings,
            BigDecimal ticketRevenue,
            BigDecimal foodRevenue,
            BigDecimal grandRevenue
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
}
