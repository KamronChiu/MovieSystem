package com.eduaccess.service;

import com.eduaccess.domain.*;
import com.eduaccess.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT_009 / IT_010 — Integration tests for {@link BookingService}.
 * <p>
 * Tests the booking creation flow end-to-end with a real Spring context:
 * seat validation, pricing calculation, unique reference generation, and
 * boundary conditions (past screening, already-booked seat).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking-test;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "vaadin.launch-browser=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EntityManager entityManager;

    private Screening futureScreening;
    private Seat seatA1;
    private Seat seatA2;

    @BeforeEach
    void setUp() {
        Cinema cinema = new Cinema("Booking Cinema", "London", "5 Booking Rd");
        entityManager.persist(cinema);

        Screen screen = new Screen(cinema, 1, 100, HallType.REGULAR);
        entityManager.persist(screen);

        seatA1 = new Seat(screen, "A1", SeatType.STANDARD);
        seatA2 = new Seat(screen, "A2", SeatType.PREMIUM);
        entityManager.persist(seatA1);
        entityManager.persist(seatA2);

        Film film = new Film("Booking Film", "A great film", "Actor X",
                "Drama", "12A", 110);
        entityManager.persist(film);

        // Use +1 day — within the 7-day booking window
        futureScreening = new Screening(film, screen,
                LocalDate.now().plusDays(1), LocalTime.of(19, 30));
        entityManager.persist(futureScreening);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("createBooking_validInput_persistsBookingWithCorrectTotal")
    void createBooking_validInput_persistsBookingWithCorrectTotal() {
        Booking booking = bookingService.createBooking(
                futureScreening.getId(),
                List.of(seatA1.getId(), seatA2.getId()),
                "Test Customer",
                "test@example.com"
        );

        assertThat(booking).isNotNull();
        assertThat(booking.getBookingReference()).startsWith("HCBS-");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getBookingSeats()).hasSize(2);
        assertThat(booking.getTotalCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(booking.getCustomerName()).isEqualTo("Test Customer");
        assertThat(booking.getCustomerEmail()).isEqualTo("test@example.com");

        // Verify persistence
        assertThat(bookingRepository.findByBookingReference(booking.getBookingReference()))
                .isPresent();
    }

    @Test
    @DisplayName("createBooking_pastScreening_throwsException")
    void createBooking_pastScreening_throwsException() {
        // Create a past screening
        Film film = entityManager.find(Film.class,
                futureScreening.getFilm().getId());
        Screen screen = entityManager.find(Screen.class,
                futureScreening.getScreen().getId());
        Screening pastScreening = new Screening(film, screen,
                LocalDate.now().minusDays(1), LocalTime.of(10, 0));
        entityManager.persist(pastScreening);
        entityManager.flush();

        assertThatThrownBy(() -> bookingService.createBooking(
                pastScreening.getId(),
                List.of(seatA1.getId()),
                "Customer",
                "c@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("past screening");
    }

    @Test
    @DisplayName("createBooking_alreadyBookedSeat_throwsException")
    void createBooking_alreadyBookedSeat_throwsException() {
        // First booking takes seatA1
        bookingService.createBooking(
                futureScreening.getId(),
                List.of(seatA1.getId()),
                "First Customer",
                "first@test.com"
        );

        // Second attempt on same seat should fail
        assertThatThrownBy(() -> bookingService.createBooking(
                futureScreening.getId(),
                List.of(seatA1.getId()),
                "Second Customer",
                "second@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    @DisplayName("createBooking_noSeats_throwsException")
    void createBooking_noSeats_throwsException() {
        assertThatThrownBy(() -> bookingService.createBooking(
                futureScreening.getId(),
                List.of(),
                "Customer",
                "c@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one seat");
    }

    @Test
    @DisplayName("createBooking_invalidEmail_throwsException")
    void createBooking_invalidEmail_throwsException() {
        assertThatThrownBy(() -> bookingService.createBooking(
                futureScreening.getId(),
                List.of(seatA1.getId()),
                "Customer",
                "not-an-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("findAvailableSeats_afterBooking_excludesBookedSeat")
    void findAvailableSeats_afterBooking_excludesBookedSeat() {
        // Before booking — both seats available
        List<Seat> before = bookingService.findAvailableSeats(futureScreening.getId());
        assertThat(before).hasSize(2);

        // Book seatA1
        bookingService.createBooking(
                futureScreening.getId(),
                List.of(seatA1.getId()),
                "Customer",
                "c@test.com"
        );

        // After booking — only seatA2 available
        List<Seat> after = bookingService.findAvailableSeats(futureScreening.getId());
        assertThat(after).hasSize(1);
        assertThat(after.get(0).getSeatNumber()).isEqualTo("A2");
    }

    // ═══ Additional boundary / edge-case tests ════════════════════════════════════════

    @Test
    @DisplayName("createBooking_nullSeatList_throwsException")
    void createBooking_nullSeatList_throwsException() {
        assertThatThrownBy(() -> bookingService.createBooking(
                futureScreening.getId(),
                null,
                "Customer",
                "c@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one seat");
    }

    @Test
    @DisplayName("createBooking_tooFarInAdvance_throwsException")
    void createBooking_tooFarInAdvance_throwsException() {
        // Create a screening 10 days from now (>7 day limit)
        Film film = entityManager.find(Film.class,
                futureScreening.getFilm().getId());
        Screen screen = entityManager.find(Screen.class,
                futureScreening.getScreen().getId());
        Screening farScreening = new Screening(film, screen,
                LocalDate.now().plusDays(10), LocalTime.of(20, 0));
        entityManager.persist(farScreening);
        entityManager.flush();

        assertThatThrownBy(() -> bookingService.createBooking(
                farScreening.getId(),
                List.of(seatA1.getId()),
                "Customer",
                "c@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one week in advance");
    }

    @Test
    @DisplayName("createBooking_nullCustomerName_throwsException")
    void createBooking_nullCustomerName_throwsException() {
        assertThatThrownBy(() -> bookingService.createBooking(
                futureScreening.getId(),
                List.of(seatA1.getId()),
                null,
                "c@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer name");
    }

    @Test
    @DisplayName("createBooking_blankCustomerName_throwsException")
    void createBooking_blankCustomerName_throwsException() {
        assertThatThrownBy(() -> bookingService.createBooking(
                futureScreening.getId(),
                List.of(seatA1.getId()),
                "   ",
                "c@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer name");
    }

    @Test
    @DisplayName("createBooking_nullEmail_throwsException")
    void createBooking_nullEmail_throwsException() {
        assertThatThrownBy(() -> bookingService.createBooking(
                futureScreening.getId(),
                List.of(seatA1.getId()),
                "Customer",
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("createBooking_nonExistentScreening_throwsException")
    void createBooking_nonExistentScreening_throwsException() {
        assertThatThrownBy(() -> bookingService.createBooking(
                99999L,
                List.of(seatA1.getId()),
                "Customer",
                "c@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Screening not found");
    }
}
