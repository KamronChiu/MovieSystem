package com.eduaccess.service;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.CancellationRecord;
import com.eduaccess.domain.RefundSummary;
import com.eduaccess.exception.CancellationNotAllowedException;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.CancellationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CancellationService {

    private final BookingRepository bookingRepository;
    private final RefundCalculator refundCalculator;
    private final CancellationRepository cancellationRepository;
    private final AuditService auditService;

    public CancellationService(BookingRepository bookingRepository,
                               RefundCalculator refundCalculator,
                               CancellationRepository cancellationRepository,
                               AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.refundCalculator = refundCalculator;
        this.cancellationRepository = cancellationRepository;
        this.auditService = auditService;
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
        boolean previous = booking.isVip();
        booking.setVip(vip);
        Booking saved = bookingRepository.save(booking);

        // TASK 7 — audit the VIP toggle (only when it actually changes).
        if (previous != vip) {
            auditService.record(
                    AuditService.ACTION_UPDATE_VIP,
                    saved.getBookingReference(),
                    saved.getStatus(),
                    saved.getStatus(),
                    "VIP flag set to " + vip
            );
        }
        return saved;
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

        BookingStatus oldStatus = booking.getStatus();
        booking.transitionTo(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        // TASK 6 — persist a cancellation record for this booking.
        syncCancellationRecord(savedBooking, refundSummary, null);

        // TASK 7 — record the cancellation in the audit log.
        auditService.record(
                AuditService.ACTION_CANCEL_BOOKING,
                savedBooking.getBookingReference(),
                oldStatus,
                savedBooking.getStatus(),
                "Booking cancelled, refund " + refundSummary.getRefundAmount()
        );

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

        BookingStatus oldStatus = booking.getStatus();
        booking.transitionTo(targetStatus);
        Booking saved = bookingRepository.save(booking);

        // TASK 6 — keep the cancellation record in sync with the new status.
        syncCancellationRecord(saved, refundCalculator.calculate(saved), null);

        // TASK 7 — record the transition in the audit log.
        auditService.record(
                AuditService.ACTION_ADVANCE_STATUS,
                saved.getBookingReference(),
                oldStatus,
                saved.getStatus(),
                "Status advanced from " + oldStatus.getDisplayName()
                        + " to " + saved.getStatus().getDisplayName()
        );

        return saved;
    }

    // ── TASK 6 — Cancellation record persistence ──────────────────────────

    /**
     * Updates (or lazily creates) the cancellation record for the given
     * booking with a user-supplied reason.
     * <p>
     * If the booking has not yet transitioned out of {@code CONFIRMED} no
     * record exists yet — in that case the call is a no-op so the reason
     * can be re-submitted later when the cancellation is finalised.
     *
     * @param bookingReference the booking reference
     * @param reason           the user-supplied reason (may be blank)
     * @return the persisted record, or {@code null} if no record exists yet
     */
    @Transactional
    public CancellationRecord updateCancellationReason(String bookingReference, String reason) {
        if (bookingReference == null || bookingReference.isBlank()) {
            return null;
        }
        String ref = normalizeReference(bookingReference);
        CancellationRecord record = cancellationRepository.findByBookingReference(ref)
                .orElse(null);
        if (record == null) {
            return null;
        }
        String previous = record.getCancellationReason();
        String next = reason == null ? "" : reason.trim();
        record.setCancellationReason(next);
        CancellationRecord saved = cancellationRepository.save(record);

        // TASK 7 — audit reason updates only when the text actually changed.
        if (!java.util.Objects.equals(previous == null ? "" : previous, next)) {
            auditService.record(
                    AuditService.ACTION_UPDATE_REASON,
                    saved.getBookingReference(),
                    null,
                    null,
                    next.isBlank() ? "Reason cleared" : "Reason: " + next
            );
        }
        return saved;
    }

    /**
     * Returns the cancellation record for the given booking, if any.
     *
     * @param bookingReference the booking reference
     * @return the record, or empty if the booking has never been cancelled
     */
    @Transactional(readOnly = true)
    public Optional<CancellationRecord> findCancellationRecord(String bookingReference) {
        if (bookingReference == null || bookingReference.isBlank()) {
            return Optional.empty();
        }
        return cancellationRepository.findByBookingReference(normalizeReference(bookingReference));
    }

    /**
     * Returns every cancellation record ordered most recent first.
     *
     * @return all cancellation records, newest first
     */
    @Transactional(readOnly = true)
    public List<CancellationRecord> findAllCancellationRecords() {
        return cancellationRepository.findAllByOrderByCancelledAtDesc();
    }

    /**
     * Internal hook invoked after every successful status transition.
     * <p>
     * <ul>
     *   <li>Booking is still {@code CONFIRMED}: nothing to do.</li>
     *   <li>Record does not yet exist: insert one with the current refund
     *       snapshot and {@code refunded = (status == REFUNDED)}.</li>
     *   <li>Record exists: refresh the refund amount with the latest
     *       calculation (the refund amount may shift if VIP was toggled),
     *       and flip {@code refunded = true} once the booking reaches
     *       {@code REFUNDED}.</li>
     * </ul>
     *
     * @param booking       the booking after its status was updated
     * @param refundSummary the refund summary for the booking (may be null)
     * @param reason        an optional reason override (may be null)
     */
    private void syncCancellationRecord(Booking booking,
                                        RefundSummary refundSummary,
                                        String reason) {
        BookingStatus status = booking.getStatus();
        if (status == BookingStatus.CONFIRMED) {
            return;
        }

        String ref = booking.getBookingReference();
        BigDecimal refundAmount = refundSummary != null
                ? refundSummary.getRefundAmount()
                : BigDecimal.ZERO;

        CancellationRecord record = cancellationRepository.findByBookingReference(ref)
                .orElse(null);

        if (record == null) {
            record = new CancellationRecord(
                    ref,
                    refundAmount,
                    reason == null ? "" : reason.trim(),
                    LocalDateTime.now(),
                    status == BookingStatus.REFUNDED
            );
        } else {
            record.setRefundAmount(refundAmount);
            if (reason != null) {
                record.setCancellationReason(reason.trim());
            }
            if (status == BookingStatus.REFUNDED) {
                record.setRefunded(true);
            }
        }
        cancellationRepository.save(record);
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
