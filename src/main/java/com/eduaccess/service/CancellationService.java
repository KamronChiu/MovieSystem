package com.eduaccess.service;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.RefundSummary;
import com.eduaccess.exception.CancellationNotAllowedException;
import com.eduaccess.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class CancellationService {

    private final BookingRepository bookingRepository;
    private final RefundCalculator refundCalculator;

    public CancellationService(BookingRepository bookingRepository,
                               RefundCalculator refundCalculator) {
        this.bookingRepository = bookingRepository;
        this.refundCalculator = refundCalculator;
    }

    @Transactional(readOnly = true)
    public List<Booking> findAllBookings() {
        return bookingRepository.findAllByOrderByBookingDateDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Booking> findBookingByReference(String bookingReference) {
        if (bookingReference == null || bookingReference.isBlank()) {
            return Optional.empty();
        }

        return bookingRepository.findByBookingReference(normalizeReference(bookingReference));
    }

    /**
     * Updates the VIP flag for the given booking and persists it.
     *
     * @param bookingReference the booking reference
     * @param vip              whether the customer is VIP
     * @return the updated booking, or null if not found
     */
    @Transactional
    public Booking updateVipFlag(String bookingReference, boolean vip) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElse(null);
        if (booking == null) {
            return null;
        }
        booking.setVip(vip);
        return bookingRepository.save(booking);
    }

    /**
     * Calculates the refund summary for a booking without changing its status.
     * <p>
     * This method is intended for preview purposes — the UI can call it to
     * display the refund breakdown before the user confirms the cancellation.
     *
     * @param bookingReference the booking reference
     * @return the refund summary, or null if the booking is not found
     */
    @Transactional(readOnly = true)
    public RefundSummary calculateRefund(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElse(null);
        if (booking == null) {
            return null;
        }
        return refundCalculator.calculate(booking);
    }

    /**
     * Cancels the booking identified by the given reference.
     * <p>
     * This is a convenience method that transitions CONFIRMED → CANCELLED
     * in a single call. For the full step-by-step refund flow use
     * {@link #advanceStatus(String, BookingStatus)}.
     *
     * @param bookingReference the booking reference to cancel
     * @return cancellation result with charge and refund details
     * @throws CancellationNotAllowedException if the booking status does not
     *         allow cancellation
     */
    @Transactional
    public CancellationResult cancelBooking(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElseThrow(() -> new CancellationNotAllowedException(
                        "Booking reference was not found."));

        if (!booking.getStatus().isCancellable()) {
            throw new CancellationNotAllowedException(
                    "Booking cannot be cancelled. Current status: "
                            + booking.getStatus().getDisplayName());
        }

        // Same-day cancellation is now allowed; refund will be £0
        // (RefundCalculator handles this)

        RefundSummary refundSummary = refundCalculator.calculate(booking);

        booking.transitionTo(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        return new CancellationResult(savedBooking, refundSummary);
    }

    /**
     * Advances the booking to the next status in the refund flow.
     * <p>
     * The target status must be a valid transition from the booking's current
     * status as defined by {@link BookingStatus#canTransitionTo(BookingStatus)}.
     * <p>
     * Same-day cancellations are now permitted — the refund amount will be
     * £0 but the flow can still proceed through all steps to REFUNDED.
     *
     * @param bookingReference the booking reference
     * @param targetStatus     the target status to transition to
     * @return the updated booking
     * @throws CancellationNotAllowedException if the booking is not found or
     *         the transition is not allowed
     */
    @Transactional
    public Booking advanceStatus(String bookingReference, BookingStatus targetStatus) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElseThrow(() -> new CancellationNotAllowedException(
                        "Booking reference was not found."));

        if (!booking.getStatus().canTransitionTo(targetStatus)) {
            throw new CancellationNotAllowedException(
                    "Cannot transition from " + booking.getStatus().getDisplayName()
                            + " to " + targetStatus.getDisplayName());
        }

        // No date restriction — same-day cancellations are allowed
        // (refund will be £0 per RefundCalculator)

        booking.transitionTo(targetStatus);
        return bookingRepository.save(booking);
    }

    private String normalizeReference(String bookingReference) {
        return bookingReference.trim().toUpperCase();
    }

    /**
     * Result of a cancellation operation, carrying the updated booking
     * and the full refund summary with adjustment breakdown.
     */
    public record CancellationResult(
            Booking booking,
            RefundSummary refundSummary
    ) {
    }
}
