package com.eduaccess.service;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
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

    private static final BigDecimal CANCELLATION_RATE = new BigDecimal("0.50");

    private final BookingRepository bookingRepository;

    public CancellationService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
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

    @Transactional
    public CancellationResult cancelBooking(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElseThrow(() -> new IllegalArgumentException("Booking reference was not found."));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("This booking has already been cancelled.");
        }

        validateCancellationDate(booking);

        BigDecimal cancellationCharge = booking.getTotalCost()
                .multiply(CANCELLATION_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal refundAmount = booking.getTotalCost()
                .subtract(cancellationCharge)
                .setScale(2, RoundingMode.HALF_UP);

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        return new CancellationResult(savedBooking, cancellationCharge, refundAmount);
    }

    private void validateCancellationDate(Booking booking) {
        LocalDate today = LocalDate.now();
        LocalDate screeningDate = booking.getScreening().getScreeningDate();

        if (!screeningDate.isAfter(today)) {
            throw new IllegalStateException(
                    "Cancellation is not allowed on the day of the show or after the show."
            );
        }
    }

    private String normalizeReference(String bookingReference) {
        return bookingReference.trim().toUpperCase();
    }

    public record CancellationResult(
            Booking booking,
            BigDecimal cancellationCharge,
            BigDecimal refundAmount
    ) {
    }
}