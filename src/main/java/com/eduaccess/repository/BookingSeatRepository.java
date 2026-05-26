package com.eduaccess.repository;

import com.eduaccess.domain.BookingSeat;
import com.eduaccess.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

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

    @Query(value = """
            SELECT bs.seat_id
            FROM booking_seats bs
            JOIN bookings b ON bs.booking_id = b.id
            WHERE b.screening_id = :screeningId
              AND b.status = 'CONFIRMED'
            """, nativeQuery = true)
    Set<Long> findBookedSeatIdsByScreeningId(@Param("screeningId") Long screeningId);
    @Query("""
            select bs.seat.seatNumber
            from BookingSeat bs
            where bs.booking.id = :bookingId
            order by bs.seat.seatNumber asc
            """)
    List<String> findSeatNumbersByBookingId(@Param("bookingId") Long bookingId);

}