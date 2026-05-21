package com.eduaccess.repository;

import com.eduaccess.domain.Screening;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findAllByOrderByScreeningDateAscStartTimeAsc();

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findByScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
            LocalDate startDate,
            LocalDate endDate
    );

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findByScreenCinemaIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
            Long cinemaId,
            LocalDate startDate,
            LocalDate endDate
    );

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findByFilmIdOrderByScreeningDateAscStartTimeAsc(Long filmId);

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findByScreenIdAndScreeningDateOrderByStartTimeAsc(
            Long screenId,
            LocalDate screeningDate
    );

    @Query("""
            select count(s)
            from Screening s
            where s.screen.id = :screenId
              and s.screeningDate = :screeningDate
            """)
    long countByScreenAndDate(
            @Param("screenId") Long screenId,
            @Param("screeningDate") LocalDate screeningDate
    );

    @Query("""
            select count(b) > 0
            from Booking b
            where b.screening.id = :screeningId
            """)
    boolean hasAnyBookingsForScreening(@Param("screeningId") Long screeningId);

    @Modifying
    @Query(
            value = """
                    DELETE FROM screenings
                    WHERE screening_date < :today
                      AND id NOT IN (
                          SELECT screening_id
                          FROM bookings
                      )
                    """,
            nativeQuery = true
    )
    int deleteExpiredUnbookedScreenings(@Param("today") LocalDate today);
}