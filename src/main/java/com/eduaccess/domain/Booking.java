package com.eduaccess.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true)
    private String bookingReference;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "booking_date", nullable = false)
    private LocalDateTime bookingDate;

    @Column(name = "total_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    /**
     * Booking lifecycle status.
     * <p>
     * The column is declared as a plain {@code VARCHAR(32)} via
     * {@link Column#columnDefinition()} so Hibernate does NOT emit an
     * automatic {@code CHECK ... IN (...)} constraint for the enum.
     * Without this override, expanding the {@link BookingStatus} enum
     * (e.g. adding {@code REFUND_PENDING} / {@code REFUNDED}) on top of an
     * existing H2 file is rejected with H2 error 23513 because
     * {@code ddl-auto=update} never modifies pre-existing CHECK constraints.
     * State-transition validity is enforced in the domain layer
     * ({@link BookingStatus#canTransitionTo(BookingStatus)}), not at the
     * database level.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL")
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "vip")
    private Boolean vip = false;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSeat> bookingSeats = new ArrayList<>();

    protected Booking() {
    }

    @PrePersist
    @PreUpdate
    private void ensureDefaults() {
        if (vip == null) {
            vip = false;
        }
    }

    public Booking(
            String bookingReference,
            Screening screening,
            String customerName,
            String customerEmail
    ) {
        this.bookingReference = bookingReference;
        this.screening = screening;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.bookingDate = LocalDateTime.now();
        this.status = BookingStatus.CONFIRMED;
        this.totalCost = BigDecimal.ZERO;
    }

    public void addBookingSeat(BookingSeat bookingSeat) {
        bookingSeats.add(bookingSeat);
        bookingSeat.setBooking(this);
    }

    public void removeBookingSeat(BookingSeat bookingSeat) {
        bookingSeats.remove(bookingSeat);
        bookingSeat.setBooking(null);
    }

    public Long getId() {
        return id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public Screening getScreening() {
        return screening;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public LocalDateTime getBookingDate() {
        return bookingDate;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public List<BookingSeat> getBookingSeats() {
        return bookingSeats;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public void setScreening(Screening screening) {
        this.screening = screening;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public void setBookingDate(LocalDateTime bookingDate) {
        this.bookingDate = bookingDate;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public void setBookingSeats(List<BookingSeat> bookingSeats) {
        this.bookingSeats = bookingSeats;
    }

    public Boolean getVip() {
        return vip;
    }

    public void setVip(Boolean vip) {
        this.vip = vip;
    }

    public boolean isVip() {
        return Boolean.TRUE.equals(vip);
    }

    /**
     * Transitions this booking to the given target status.
     * <p>
     * The actual state-transition rule is defined inside
     * {@link BookingStatus#canTransitionTo(BookingStatus)}, keeping the enum
     * as the single source of truth for all transition rules.
     *
     * @param target the desired next status
     * @throws IllegalStateException if the current status does not allow
     *         this transition
     */
    public void transitionTo(BookingStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition from " + status.getDisplayName()
                            + " to " + target.getDisplayName()
            );
        }
        this.status = target;
    }
}