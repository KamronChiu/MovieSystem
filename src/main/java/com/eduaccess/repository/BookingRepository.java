package com.eduaccess.repository;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByBookingReference(String bookingReference);

    @EntityGraph(attributePaths = {
            "screening",
            "screening.film",
            "screening.screen",
            "screening.screen.cinema",
            "bookingSeats",
            "bookingSeats.seat"
    })
    Optional<Booking> findByBookingReference(String bookingReference);

    @EntityGraph(attributePaths = {
            "screening",
            "screening.film",
            "screening.screen",
            "screening.screen.cinema",
            "bookingSeats",
            "bookingSeats.seat"
    })
    List<Booking> findAllByOrderByBookingDateDesc();

    @Query("""
            select count(bs) > 0
            from BookingSeat bs
            where bs.booking.screening.id = :screeningId
              and bs.seat.id = :seatId
              and bs.booking.status = :status
            """)
    boolean existsBookedSeat(
            @Param("screeningId") Long screeningId,
            @Param("seatId") Long seatId,
            @Param("status") BookingStatus status
    );
}