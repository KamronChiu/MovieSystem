package com.eduaccess.service;

import com.eduaccess.domain.*;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.BookingSeatRepository;
import com.eduaccess.repository.ScreeningRepository;
import com.eduaccess.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final PricingService pricingService;

    public BookingService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository,
            PricingService pricingService
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.screeningRepository = screeningRepository;
        this.seatRepository = seatRepository;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public List<Seat> findAvailableSeats(Long screeningId) {
        Screening screening = screeningRepository.findById(screeningId)
                .orElseThrow(() -> new IllegalArgumentException("Screening not found."));

        List<Seat> allSeats = seatRepository.findByScreenIdOrderBySeatNumberAsc(
                screening.getScreen().getId()
        );

        List<Long> bookedSeatIds = bookingSeatRepository.findBookedSeatIdsByScreeningId(
                screeningId,
                BookingStatus.CONFIRMED
        );

        return allSeats.stream()
                .filter(seat -> !bookedSeatIds.contains(seat.getId()))
                .toList();
    }

    @Transactional
    public Booking createBooking(
            Long screeningId,
            Collection<Long> seatIds,
            String customerName,
            String customerEmail
    ) {
        validateCustomer(customerName, customerEmail);

        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one seat.");
        }

        List<Long> distinctSeatIds = seatIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctSeatIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one valid seat.");
        }

        Screening screening = screeningRepository.findById(screeningId)
                .orElseThrow(() -> new IllegalArgumentException("Screening not found."));

        validateBookingDate(screening);

        List<Seat> seats = seatRepository.findAllById(distinctSeatIds);

        if (seats.size() != distinctSeatIds.size()) {
            throw new IllegalArgumentException("One or more selected seats could not be found.");
        }

        for (Seat seat : seats) {
            validateSeatBelongsToScreening(seat, screening);
            validateSeatIsAvailable(seat, screening);
        }

        String bookingReference = generateUniqueBookingReference();

        Booking booking = new Booking(
                bookingReference,
                screening,
                customerName.trim(),
                customerEmail.trim()
        );

        BigDecimal totalCost = BigDecimal.ZERO;

        for (Seat seat : seats) {
            BigDecimal ticketPrice = pricingService.calculateTicketPrice(screening, seat);
            BookingSeat bookingSeat = new BookingSeat(booking, seat, ticketPrice);
            booking.addBookingSeat(bookingSeat);
            totalCost = totalCost.add(ticketPrice);
        }

        booking.setTotalCost(totalCost);

        return bookingRepository.save(booking);
    }

    private void validateCustomer(String customerName, String customerEmail) {
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("Customer name is required.");
        }

        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException("Customer email is required.");
        }

        if (!customerEmail.contains("@")) {
            throw new IllegalArgumentException("Customer email must be valid.");
        }
    }

    private void validateBookingDate(Screening screening) {
        LocalDate today = LocalDate.now();
        LocalDate screeningDate = screening.getScreeningDate();

        if (screeningDate.isBefore(today)) {
            throw new IllegalArgumentException("Cannot book tickets for a past screening.");
        }

        if (screeningDate.isAfter(today.plusDays(7))) {
            throw new IllegalArgumentException("Tickets can only be booked up to one week in advance.");
        }
    }

    private void validateSeatBelongsToScreening(Seat seat, Screening screening) {
        Long seatScreenId = seat.getScreen().getId();
        Long screeningScreenId = screening.getScreen().getId();

        if (!seatScreenId.equals(screeningScreenId)) {
            throw new IllegalArgumentException(
                    "Seat " + seat.getSeatNumber() + " does not belong to this screening's screen."
            );
        }
    }

    private void validateSeatIsAvailable(Seat seat, Screening screening) {
        boolean alreadyBooked = bookingRepository.existsBookedSeat(
                screening.getId(),
                seat.getId(),
                BookingStatus.CONFIRMED
        );

        if (alreadyBooked) {
            throw new IllegalStateException(
                    "Seat " + seat.getSeatNumber() + " is already booked for this screening."
            );
        }
    }

    private String generateUniqueBookingReference() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        for (int attempt = 0; attempt < 20; attempt++) {
            String randomPart = UUID.randomUUID()
                    .toString()
                    .substring(0, 6)
                    .toUpperCase();

            String reference = "HCBS-" + datePart + "-" + randomPart;

            if (!bookingRepository.existsByBookingReference(reference)) {
                return reference;
            }
        }

        throw new IllegalStateException("Could not generate a unique booking reference.");
    }
    @Transactional(readOnly = true)
    public List<SeatOption> findSeatOptions(Long screeningId) {
        Screening screening = screeningRepository.findById(screeningId)
                .orElseThrow(() -> new IllegalArgumentException("Screening not found."));

        List<Seat> allSeats = seatRepository.findByScreenIdOrderBySeatNumberAsc(
                screening.getScreen().getId()
        );

        Set<Long> bookedSeatIds = new HashSet<>(
                bookingSeatRepository.findBookedSeatIdsByScreeningId(
                        screeningId,
                        BookingStatus.CONFIRMED
                )
        );

        return allSeats.stream()
                .map(seat -> new SeatOption(seat, !bookedSeatIds.contains(seat.getId())))
                .toList();
    }

    public record SeatOption(Seat seat, boolean available) {
    }
}