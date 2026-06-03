package com.eduaccess.service;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.RefundSummary;
import com.eduaccess.domain.Screening;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UT_008 — Unit tests for {@link RefundCalculator}.
 * <p>
 * Uses Mockito mocks for Booking / Screening so we can drive every
 * branch (same-day, weekend, VIP) deterministically without a database.
 */
class RefundCalculatorTest {

    private final RefundCalculator calculator = new RefundCalculator();

    @Test
    @DisplayName("calculate_standardCancellation_returnsHalfRefund")
    void calculate_standardCancellation_returnsHalfRefund() {
        // Pick a Wednesday (clearly not a weekend) several days in the future
        // so the calculator never falls into the same-day or weekend branch.
        LocalDate futureWednesday = nextWeekday(DayOfWeek.WEDNESDAY);

        Booking booking = mockBooking(new BigDecimal("20.00"), false, futureWednesday);

        RefundSummary summary = calculator.calculate(booking);

        // 20 × 0.50 = 10.00
        assertThat(summary.getRefundAmount()).isEqualByComparingTo("10.00");
        assertThat(summary.getOriginalAmount()).isEqualByComparingTo("20.00");
        assertThat(summary.getFeeAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("calculate_sameDay_returnsZeroRefund")
    void calculate_sameDay_returnsZeroRefund() {
        Booking booking = mockBooking(new BigDecimal("20.00"), false, LocalDate.now());

        RefundSummary summary = calculator.calculate(booking);

        // Same-day rule overrides all others — refund = 0.
        assertThat(summary.getRefundAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.getFeeAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("calculate_vipBooking_addsTwentyPercentBonus")
    void calculate_vipBooking_addsTwentyPercentBonus() {
        LocalDate futureWednesday = nextWeekday(DayOfWeek.WEDNESDAY);
        Booking booking = mockBooking(new BigDecimal("20.00"), true, futureWednesday);

        RefundSummary summary = calculator.calculate(booking);

        // base = 10.00; VIP +20% = 12.00
        assertThat(summary.getRefundAmount()).isEqualByComparingTo("12.00");
    }

    @Test
    @DisplayName("calculate_weekendScreening_appliesPenalty")
    void calculate_weekendScreening_appliesPenalty() {
        LocalDate futureSaturday = nextWeekday(DayOfWeek.SATURDAY);
        Booking booking = mockBooking(new BigDecimal("20.00"), false, futureSaturday);

        RefundSummary summary = calculator.calculate(booking);

        // base = 10.00; weekend −10% = 9.00
        assertThat(summary.getRefundAmount()).isEqualByComparingTo("9.00");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Booking mockBooking(BigDecimal totalCost, boolean vip, LocalDate screeningDate) {
        Booking booking = mock(Booking.class);
        Screening screening = mock(Screening.class);
        when(booking.getTotalCost()).thenReturn(totalCost);
        when(booking.isVip()).thenReturn(vip);
        when(booking.getScreening()).thenReturn(screening);
        when(screening.getScreeningDate()).thenReturn(screeningDate);
        return booking;
    }

    /** Returns the next future date with the requested day-of-week. */
    private LocalDate nextWeekday(DayOfWeek target) {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != target) {
            d = d.plusDays(1);
        }
        return d;
    }
}
