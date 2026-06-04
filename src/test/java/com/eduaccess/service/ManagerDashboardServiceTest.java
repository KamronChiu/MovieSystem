package com.eduaccess.service;

import com.eduaccess.domain.*;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.FoodOrderRepository;
import com.eduaccess.repository.ManagerFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * UT_022 ~ UT_024 — Unit tests for {@link ManagerDashboardService}.
 */
@ExtendWith(MockitoExtension.class)
class ManagerDashboardServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private FoodOrderRepository foodOrderRepository;
    @Mock private ManagerFeedbackRepository feedbackRepository;
    @Mock private LoginService loginService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ManagerDashboardService dashboardService;

    private Cinema cinema;
    private Screen screen;
    private Film film;

    @BeforeEach
    void setUp() {
        cinema = new Cinema("HCBS Bank", "London", "1 Bank St");
        screen = new Screen(cinema, 1, 100, HallType.REGULAR);
        film = new Film("Top Film", "Desc", "Actor", "Action", "12A", 120);
    }

    @Test
    @DisplayName("UT_022 foodAttachRate_calculatesCorrectly")
    void foodAttachRate_calculatesCorrectly() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(7);

        Screening screening = new Screening(film, screen, today, LocalTime.of(19, 0));
        Booking b1 = createBooking("HCBS-UT022-001", screening, BigDecimal.TEN);
        Booking b2 = createBooking("HCBS-UT022-002", screening, BigDecimal.TEN);
        Booking b3 = createBooking("HCBS-UT022-003", screening, BigDecimal.TEN);
        Booking b4 = createBooking("HCBS-UT022-004", screening, BigDecimal.TEN);

        // 4 confirmed bookings
        when(bookingRepository.findAllByOrderByBookingDateDesc())
                .thenReturn(List.of(b1, b2, b3, b4));

        // 2 active food orders (one for b1, one for b2)
        FoodOrder fo1 = new FoodOrder(b1, DeliveryMethod.COUNTER_PICKUP);
        fo1.setTotalCost(new BigDecimal("5.00"));
        FoodOrder fo2 = new FoodOrder(b2, DeliveryMethod.DELIVER_TO_SEAT);
        fo2.setTotalCost(new BigDecimal("3.00"));

        when(foodOrderRepository.findAllByOrderByOrderTimeDesc())
                .thenReturn(List.of(fo1, fo2));
        when(feedbackRepository.findTop20ByOrderByCreatedAtDesc())
                .thenReturn(List.of());

        ManagerDashboardService.DashboardData data = dashboardService.buildDashboard(start, today);

        // ratio = 2 food orders / 4 confirmed bookings * 100 = 50.0%
        assertThat(data.summary().foodAttachRate())
                .isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("UT_023 recommendations_highDemandFilm_suggestsExtraScreening")
    void recommendations_highDemandFilm_suggestsExtraScreening() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(7);

        Screening screening = new Screening(film, screen, today, LocalTime.of(19, 0));

        // Multiple bookings for the same top film
        Booking b1 = createBooking("HCBS-UT023-001", screening, new BigDecimal("20.00"));
        Booking b2 = createBooking("HCBS-UT023-002", screening, new BigDecimal("20.00"));
        Booking b3 = createBooking("HCBS-UT023-003", screening, new BigDecimal("20.00"));

        when(bookingRepository.findAllByOrderByBookingDateDesc())
                .thenReturn(List.of(b1, b2, b3));
        when(foodOrderRepository.findAllByOrderByOrderTimeDesc())
                .thenReturn(List.of());
        when(feedbackRepository.findTop20ByOrderByCreatedAtDesc())
                .thenReturn(List.of());

        ManagerDashboardService.DashboardData data = dashboardService.buildDashboard(start, today);

        // The decision actions should contain a recommendation referencing the top film
        assertThat(data.decisionActions()).isNotEmpty();
        boolean hasTopFilmRecommendation = data.decisionActions().stream()
                .anyMatch(action -> action.title().contains("Top Film")
                        || action.description().contains("Top Film"));
        assertThat(hasTopFilmRecommendation)
                .as("Top film should trigger a capacity recommendation")
                .isTrue();
    }

    @Test
    @DisplayName("heatmap_groupsBySlotAndDay")
    void heatmap_groupsBySlotAndDay() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(7);

        // Create a booking on a known day with an evening screening (19:00 → Evening slot)
        // Find next Wednesday from start
        LocalDate wednesday = start;
        while (wednesday.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            wednesday = wednesday.plusDays(1);
        }
        if (wednesday.isAfter(today)) {
            wednesday = today; // fallback
        }

        Screening eveningScreening = new Screening(film, screen, wednesday, LocalTime.of(19, 0));
        Booking b1 = createBooking("HCBS-UT024-001", eveningScreening, new BigDecimal("15.00"));

        when(bookingRepository.findAllByOrderByBookingDateDesc())
                .thenReturn(List.of(b1));
        when(foodOrderRepository.findAllByOrderByOrderTimeDesc())
                .thenReturn(List.of());
        when(feedbackRepository.findTop20ByOrderByCreatedAtDesc())
                .thenReturn(List.of());

        ManagerDashboardService.DashboardData data = dashboardService.buildDashboard(start, today);

        // Heatmap must have 7 rows (one per day)
        assertThat(data.showtimeHeatmap()).hasSize(7);

        // Find the day matching our booking
        String targetDay = wednesday.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.SHORT, java.util.Locale.UK);
        String expectedDayLabel = targetDay.substring(0, 1).toUpperCase() + targetDay.substring(1);

        data.showtimeHeatmap().stream()
                .filter(row -> row.day().equals(expectedDayLabel))
                .findFirst()
                .ifPresent(row -> {
                    // 19:00 falls in "Evening" slot (17:00–21:00)
                    ManagerDashboardService.ShowtimeHeatmapCell eveningCell = row.cells().stream()
                            .filter(c -> c.slot().equals("Evening"))
                            .findFirst()
                            .orElse(null);
                    assertThat(eveningCell).isNotNull();
                    assertThat(eveningCell.ticketsSold()).isGreaterThan(0);
                });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Booking createBooking(String ref, Screening screening, BigDecimal cost) {
        Booking booking = new Booking(ref, screening, "Customer", "c@test.com");
        booking.setTotalCost(cost);
        // Add a seat so seatCount() > 0 for heatmap/ticket calculations
        Seat seat = new Seat(screen, ref + "-A1", SeatType.STANDARD);
        booking.addBookingSeat(new BookingSeat(booking, seat, cost));
        return booking;
    }
}
