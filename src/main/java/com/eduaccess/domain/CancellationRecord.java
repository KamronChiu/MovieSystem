package com.eduaccess.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persistent audit record for every booking cancellation.
 * <p>
 * Created automatically when a booking transitions out of
 * {@link BookingStatus#CONFIRMED} into {@link BookingStatus#CANCELLED}.
 * The same record is then progressively updated as the refund flow advances
 * through {@code REFUND_PENDING} and finally {@code REFUNDED}.
 * <p>
 * Required by TASK 6 — CancellationRecord Persistence.
 */
@Entity
@Table(
        name = "cancellation_records",
        indexes = @Index(
                name = "idx_cancellation_booking_ref",
                columnList = "booking_reference"
        )
)
public class CancellationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Booking that was cancelled (business key, links to {@link Booking}). */
    @Column(name = "booking_reference", nullable = false, length = 80)
    private String bookingReference;

    /** Refund amount as calculated at the moment of recording. */
    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    /** Optional reason supplied by the user; never null at persistence time. */
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /** Server-side timestamp captured when the record is first created. */
    @Column(name = "cancelled_at", nullable = false)
    private LocalDateTime cancelledAt;

    /** {@code true} once the refund flow reaches {@link BookingStatus#REFUNDED}. */
    @Column(name = "refunded", nullable = false)
    private boolean refunded;

    protected CancellationRecord() {
        // Required by JPA
    }

    public CancellationRecord(
            String bookingReference,
            BigDecimal refundAmount,
            String cancellationReason,
            LocalDateTime cancelledAt,
            boolean refunded
    ) {
        this.bookingReference = bookingReference;
        this.refundAmount = refundAmount;
        this.cancellationReason = cancellationReason;
        this.cancelledAt = cancelledAt;
        this.refunded = refunded;
    }

    @PrePersist
    private void ensureDefaults() {
        if (cancelledAt == null) {
            cancelledAt = LocalDateTime.now();
        }
        if (refundAmount == null) {
            refundAmount = BigDecimal.ZERO;
        }
        if (cancellationReason == null) {
            cancellationReason = "";
        }
    }

    public Long getId() {
        return id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public boolean isRefunded() {
        return refunded;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public void setRefunded(boolean refunded) {
        this.refunded = refunded;
    }
}
