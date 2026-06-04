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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_024 — Integration test for {@link BookingSeatRepository#findBookedSeatIdsByScreeningId}.
 * Verifies that the native query only returns seat IDs from CONFIRMED bookings,
 * excluding cancelled and refunded bookings.
 */
@DataJpaTest
class BookingSeatRepositoryIT {

    @Autowired private TestEntityManager em;
    @Autowired private BookingSeatRepository bookingSeatRepository;

    private Screening screening;
    private Seat seatA1;
    private Seat seatA2;
    private Seat seatA3;

    @BeforeEach
    void setUp() {
        Cinema cinema = new Cinema("Seat IT Cinema", "London", "1 Seat St");
        em.persist(cinema);
        Screen screen = new Screen(cinema, 1, 60, HallType.REGULAR);
        em.persist(screen);

        seatA1 = new Seat(screen, "A1", SeatType.STANDARD);
        seatA2 = new Seat(screen, "A2", SeatType.STANDARD);
        seatA3 = new Seat(screen, "A3", SeatType.PREMIUM);
        em.persist(seatA1);
        em.persist(seatA2);
        em.persist(seatA3);

        Film film = new Film("Seat Film", "Desc", "Actor", "Action", "12A", 120);
        em.persist(film);

        screening = new Screening(film, screen, LocalDate.now().plusDays(1), LocalTime.of(18, 0));
        em.persist(screening);

        em.flush();
    }

    @Test
    @DisplayName("IT_024 findBookedSeatIdsByScreeningId_returnsConfirmedOnly")
    void findBookedSeatIdsByScreeningId_returnsConfirmedOnly() {
        // Confirmed booking with seats A1, A2
        Booking confirmed = new Booking("HCBS-SEAT-001", screening, "Alice", "alice@test.com");
        confirmed.setTotalCost(new BigDecimal("20.00"));
        confirmed.addBookingSeat(new BookingSeat(confirmed, seatA1, BigDecimal.TEN));
        confirmed.addBookingSeat(new BookingSeat(confirmed, seatA2, BigDecimal.TEN));
        em.persist(confirmed);

        // Cancelled booking with seat A3 — should NOT appear in results
        Booking cancelled = new Booking("HCBS-SEAT-002", screening, "Bob", "bob@test.com");
        cancelled.setTotalCost(BigDecimal.TEN);
        cancelled.setStatus(BookingStatus.CANCELLED);
        cancelled.addBookingSeat(new BookingSeat(cancelled, seatA3, BigDecimal.TEN));
        em.persist(cancelled);

        em.flush();

        Set<Long> bookedIds = bookingSeatRepository.findBookedSeatIdsByScreeningId(screening.getId());

        // Only confirmed booking seats should appear
        assertThat(bookedIds).containsExactlyInAnyOrder(seatA1.getId(), seatA2.getId());
        // Cancelled booking seat should be excluded
        assertThat(bookedIds).doesNotContain(seatA3.getId());
    }
}
