package com.eduaccess.repository;

import com.eduaccess.domain.Seat;
import com.eduaccess.domain.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreenIdOrderBySeatNumberAsc(Long screenId);

    List<Seat> findByScreenIdAndSeatTypeOrderBySeatNumberAsc(Long screenId, SeatType seatType);

    Optional<Seat> findByScreenIdAndSeatNumber(Long screenId, String seatNumber);
}