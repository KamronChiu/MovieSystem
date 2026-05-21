package com.eduaccess.repository;

import com.eduaccess.domain.BookingSeat;
import com.eduaccess.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    @Query("""
            select bs.seat.id
            from BookingSeat bs
            where bs.booking.screening.id = :screeningId
              and bs.booking.status = :status
            """)
    List<Long> findBookedSeatIdsByScreeningId(
            @Param("screeningId") Long screeningId,
            @Param("status") BookingStatus status
    );
}