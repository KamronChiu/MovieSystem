package com.eduaccess.service;

import com.eduaccess.domain.*;
import com.eduaccess.exception.CancellationNotAllowedException;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.CancellationRepository;
import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundScope;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT_006 / IT_007 / IT_008 — Integration tests for {@link CancellationService}.
 * <p>
 * Uses a full Spring context with in-memory H2 to verify the cancellation
 * flow end-to-end: booking creation → status transitions → cancellation
 * record persistence → policy refund submission.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cancel-test;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "vaadin.launch-browser=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CancellationServiceIntegrationTest {

    @Autowired
    private CancellationService cancellationService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CancellationRepository cancellationRepository;

    @Autowired
    private EntityManager entityManager;

    private Booking confirmedBooking;

    @BeforeEach
    void setUp() {
        Cinema cinema = new Cinema("IT Cinema", "London", "1 IT St");
        entityManager.persist(cinema);

        Screen screen = new Screen(cinema, 1, 50, HallType.REGULAR);
        entityManager.persist(screen);

        Seat seat = new Seat(screen, "A1", SeatType.STANDARD);
        entityManager.persist(seat);

        Film film = new Film("IT Film", "Desc", "Actor", "Action", "PG", 120);
        entityManager.persist(film);

        // Use tomorrow so RefundCalculator doesn't apply same-day rule
        Screening screening = new Screening(film, screen,
                LocalDate.now().plusDays(2), LocalTime.of(15, 0));
        entityManager.persist(screening);

        confirmedBooking = new Booking("HCBS-IT006-001", screening, "Alice", "alice@test.com");
        confirmedBooking.setTotalCost(new BigDecimal("20.00"));
        BookingSeat bs = new BookingSeat(confirmedBooking, seat, new BigDecimal("20.00"));
        confirmedBooking.addBookingSeat(bs);
        entityManager.persist(confirmedBooking);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("cancelBooking_confirmedBooking_transitionsToCancelled")
    void cancelBooking_confirmedBooking_transitionsToCancelled() {
        CancellationService.CancellationResult result =
                cancellationService.cancelBooking("HCBS-IT006-001");

        assertThat(result.booking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.refundSummary()).isNotNull();
        assertThat(result.refundSummary().getOriginalAmount()).isEqualByComparingTo("20.00");

        // Cancellation record should be persisted
        assertThat(cancellationRepository.findByBookingReference("HCBS-IT006-001"))
                .isPresent();
    }

    @Test
    @DisplayName("cancelBooking_alreadyCancelled_throwsException")
    void cancelBooking_alreadyCancelled_throwsException() {
        // First cancellation succeeds
        cancellationService.cancelBooking("HCBS-IT006-001");

        // Second attempt should fail
        assertThatThrownBy(() -> cancellationService.cancelBooking("HCBS-IT006-001"))
                .isInstanceOf(CancellationNotAllowedException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    @DisplayName("advanceStatus_fullFlow_CONFIRMED_to_REFUNDED")
    void advanceStatus_fullFlow_CONFIRMED_to_REFUNDED() {
        // CONFIRMED → CANCELLED
        Booking b1 = cancellationService.advanceStatus("HCBS-IT006-001", BookingStatus.CANCELLED);
        assertThat(b1.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // CANCELLED → REFUND_PENDING
        Booking b2 = cancellationService.advanceStatus("HCBS-IT006-001", BookingStatus.REFUND_PENDING);
        assertThat(b2.getStatus()).isEqualTo(BookingStatus.REFUND_PENDING);

        // REFUND_PENDING → REFUNDED
        Booking b3 = cancellationService.advanceStatus("HCBS-IT006-001", BookingStatus.REFUNDED);
        assertThat(b3.getStatus()).isEqualTo(BookingStatus.REFUNDED);

        // Cancellation record should be marked as refunded
        var record = cancellationRepository.findByBookingReference("HCBS-IT006-001");
        assertThat(record).isPresent();
        assertThat(record.get().isRefunded()).isTrue();
    }

    @Test
    @DisplayName("advanceStatus_invalidTransition_throws")
    void advanceStatus_invalidTransition_throws() {
        // CONFIRMED → REFUNDED (skipping CANCELLED) — illegal
        assertThatThrownBy(() ->
                cancellationService.advanceStatus("HCBS-IT006-001", BookingStatus.REFUNDED))
                .isInstanceOf(CancellationNotAllowedException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("updateCancellationReason_afterCancellation_persistsReason")
    void updateCancellationReason_afterCancellation_persistsReason() {
        cancellationService.cancelBooking("HCBS-IT006-001");

        CancellationRecord record = cancellationService
                .updateCancellationReason("HCBS-IT006-001", "Changed plans");

        assertThat(record).isNotNull();
        assertThat(record.getCancellationReason()).isEqualTo("Changed plans");
    }

    @Test
    @DisplayName("submitPolicyRefund_standardPartial_advancesToRefundPending")
    void submitPolicyRefund_standardPartial_advancesToRefundPending() {
        // First cancel the booking
        cancellationService.cancelBooking("HCBS-IT006-001");

        // Submit policy refund decision
        Booking result = cancellationService.submitPolicyRefund(
                "HCBS-IT006-001",
                PolicyType.STANDARD,
                RefundScope.PARTIAL,
                true,   // include movie
                false,  // no food
                false   // no VIP package
        );

        assertThat(result.getStatus()).isEqualTo(BookingStatus.REFUND_PENDING);

        // Cancellation record should have a refund amount set by policy
        var record = cancellationRepository.findByBookingReference("HCBS-IT006-001");
        assertThat(record).isPresent();
        assertThat(record.get().getRefundAmount()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("findBookingByReference_caseInsensitive_findsBooking")
    void findBookingByReference_caseInsensitive_findsBooking() {
        var found = cancellationService.findBookingByReference("hcbs-it006-001");
        assertThat(found).isPresent();
        assertThat(found.get().getBookingReference()).isEqualTo("HCBS-IT006-001");
    }

    @Test
    @DisplayName("calculateRefund_returnsNonNullSummary")
    void calculateRefund_returnsNonNullSummary() {
        RefundSummary summary = cancellationService.calculateRefund("HCBS-IT006-001");
        assertThat(summary).isNotNull();
        assertThat(summary.getOriginalAmount()).isEqualByComparingTo("20.00");
    }

    // ═══ Additional boundary / edge-case tests ════════════════════════════════════════

    @Test
    @DisplayName("cancelBooking_unknownReference_throwsException")
    void cancelBooking_unknownReference_throwsException() {
        assertThatThrownBy(() -> cancellationService.cancelBooking("HCBS-NONEXIST-999"))
                .isInstanceOf(CancellationNotAllowedException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("cancelBooking_nullReference_throwsException")
    void cancelBooking_nullReference_throwsException() {
        assertThatThrownBy(() -> cancellationService.cancelBooking(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("cancelBooking_blankReference_throwsException")
    void cancelBooking_blankReference_throwsException() {
        assertThatThrownBy(() -> cancellationService.cancelBooking("   "))
                .isInstanceOf(CancellationNotAllowedException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("submitPolicyRefund_beforeCancellation_doesNotAdvanceToRefundPending")
    void submitPolicyRefund_beforeCancellation_doesNotAdvanceToRefundPending() {
        // Booking is still CONFIRMED — cannot jump directly to REFUND_PENDING
        Booking result = cancellationService.submitPolicyRefund(
                "HCBS-IT006-001",
                PolicyType.STANDARD,
                RefundScope.FULL,
                true, false, false
        );

        // The status should remain CONFIRMED (the transition is silently skipped)
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("advanceStatus_unknownReference_throwsException")
    void advanceStatus_unknownReference_throwsException() {
        assertThatThrownBy(() ->
                cancellationService.advanceStatus("HCBS-GHOST-000", BookingStatus.CANCELLED))
                .isInstanceOf(CancellationNotAllowedException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("findBookingByReference_nullOrBlank_returnsEmpty")
    void findBookingByReference_nullOrBlank_returnsEmpty() {
        assertThat(cancellationService.findBookingByReference(null)).isEmpty();
        assertThat(cancellationService.findBookingByReference("")).isEmpty();
        assertThat(cancellationService.findBookingByReference("   ")).isEmpty();
    }

    @Test
    @DisplayName("calculateRefundableFoodAmount_nullBookingId_returnsZero")
    void calculateRefundableFoodAmount_nullBookingId_returnsZero() {
        BigDecimal amount = cancellationService.calculateRefundableFoodAmount(null);
        assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
