package com.eduaccess.repository;

import com.eduaccess.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_004 / IT_005 — Integration tests for {@link BookingRepository}.
 * <p>
 * Verifies custom queries: existsBookedSeat, countSoldSeats, totalRevenue,
 * and the EntityGraph-based finders against an in-memory H2 schema.
 */
@DataJpaTest
class BookingRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BookingRepository bookingRepository;

    private Screening screening;
    private Seat seat;

    @BeforeEach
    void setUp() {
        Cinema cinema = new Cinema("Test Cinema", "London", "1 Test St");
        em.persist(cinema);

        Screen screen = new Screen(cinema, 1, 50, HallType.REGULAR);
        em.persist(screen);

        seat = new Seat(screen, "A1", SeatType.STANDARD);
        em.persist(seat);

        Film film = new Film("Test Film", "Desc", "Actor", "Action", "PG", 120);
        em.persist(film);

        screening = new Screening(film, screen, LocalDate.now().plusDays(1), LocalTime.of(14, 0));
        em.persist(screening);

        em.flush();
    }

    @Test
    @DisplayName("findByBookingReference_returnsBookingWithSeats")
    void findByBookingReference_returnsBookingWithSeats() {
        Booking booking = new Booking("HCBS-IT004-001", screening, "Alice", "alice@test.com");
        booking.setTotalCost(new BigDecimal("12.00"));
        BookingSeat bs = new BookingSeat(booking, seat, new BigDecimal("12.00"));
        booking.addBookingSeat(bs);
        em.persist(booking);
        em.flush();
        em.clear(); // force fresh load from DB

        Optional<Booking> found = bookingRepository.findByBookingReference("HCBS-IT004-001");

        assertThat(found).isPresent();
        assertThat(found.get().getCustomerName()).isEqualTo("Alice");
        assertThat(found.get().getBookingSeats()).hasSize(1);
        assertThat(found.get().getScreening().getFilm().getTitle()).isEqualTo("Test Film");
    }

    @Test
    @DisplayName("existsBookedSeat_detectsAlreadyBookedSeat")
    void existsBookedSeat_detectsAlreadyBookedSeat() {
        Booking booking = new Booking("HCBS-IT005-001", screening, "Bob", "bob@test.com");
        booking.setTotalCost(new BigDecimal("12.00"));
        BookingSeat bs = new BookingSeat(booking, seat, new BigDecimal("12.00"));
        booking.addBookingSeat(bs);
        em.persist(booking);
        em.flush();

        boolean booked = bookingRepository.existsBookedSeat(
                screening.getId(), seat.getId(), BookingStatus.CONFIRMED);

        assertThat(booked).isTrue();
    }

    @Test
    @DisplayName("existsBookedSeat_returnsFalse_forCancelledBooking")
    void existsBookedSeat_returnsFalse_forCancelledBooking() {
        Booking booking = new Booking("HCBS-IT005-002", screening, "Carol", "carol@test.com");
        booking.setTotalCost(new BigDecimal("12.00"));
        booking.setStatus(BookingStatus.CANCELLED);
        BookingSeat bs = new BookingSeat(booking, seat, new BigDecimal("12.00"));
        booking.addBookingSeat(bs);
        em.persist(booking);
        em.flush();

        boolean booked = bookingRepository.existsBookedSeat(
                screening.getId(), seat.getId(), BookingStatus.CONFIRMED);

        assertThat(booked).isFalse();
    }

    @Test
    @DisplayName("countSoldSeatsForScreening_countsOnlyConfirmed")
    void countSoldSeatsForScreening_countsOnlyConfirmed() {
        // Confirmed booking with 1 seat
        Booking b1 = new Booking("HCBS-IT005-003", screening, "Dave", "dave@test.com");
        b1.setTotalCost(new BigDecimal("12.00"));
        BookingSeat bs1 = new BookingSeat(b1, seat, new BigDecimal("12.00"));
        b1.addBookingSeat(bs1);
        em.persist(b1);

        // Add another seat for a second confirmed booking
        Seat seat2 = new Seat(screening.getScreen(), "A2", SeatType.STANDARD);
        em.persist(seat2);
        Booking b2 = new Booking("HCBS-IT005-004", screening, "Eve", "eve@test.com");
        b2.setTotalCost(new BigDecimal("12.00"));
        BookingSeat bs2 = new BookingSeat(b2, seat2, new BigDecimal("12.00"));
        b2.addBookingSeat(bs2);
        em.persist(b2);

        // Cancelled booking — should NOT count
        Seat seat3 = new Seat(screening.getScreen(), "A3", SeatType.STANDARD);
        em.persist(seat3);
        Booking b3 = new Booking("HCBS-IT005-005", screening, "Frank", "frank@test.com");
        b3.setTotalCost(new BigDecimal("12.00"));
        b3.setStatus(BookingStatus.CANCELLED);
        BookingSeat bs3 = new BookingSeat(b3, seat3, new BigDecimal("12.00"));
        b3.addBookingSeat(bs3);
        em.persist(b3);

        em.flush();

        long count = bookingRepository.countSoldSeatsForScreening(screening.getId());
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("totalRevenueForScreening_sumsConfirmedOnly")
    void totalRevenueForScreening_sumsConfirmedOnly() {
        Booking b1 = new Booking("HCBS-IT005-006", screening, "Grace", "grace@test.com");
        b1.setTotalCost(new BigDecimal("15.00"));
        em.persist(b1);

        Booking b2 = new Booking("HCBS-IT005-007", screening, "Hank", "hank@test.com");
        b2.setTotalCost(new BigDecimal("20.00"));
        em.persist(b2);

        // Cancelled — excluded
        Booking b3 = new Booking("HCBS-IT005-008", screening, "Ivy", "ivy@test.com");
        b3.setTotalCost(new BigDecimal("10.00"));
        b3.setStatus(BookingStatus.CANCELLED);
        em.persist(b3);

        em.flush();

        BigDecimal revenue = bookingRepository.totalRevenueForScreening(screening.getId());
        assertThat(revenue).isEqualByComparingTo("35.00");
    }
}
