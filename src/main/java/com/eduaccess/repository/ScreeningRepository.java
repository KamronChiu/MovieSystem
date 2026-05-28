package com.eduaccess.repository;

import com.eduaccess.domain.Screening;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
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

    /**
     * Used by manager/admin film-listing flows before deleting a film.
     * If a film already has screenings, deleting it would break references.
     */
    boolean existsByFilmId(Long filmId);

    @Query("""
            select min(s.screeningDate)
            from Screening s
            where s.film.id = :filmId
              and s.screeningDate >= :today
            """)
    LocalDate findEarliestUpcomingScreeningDateForFilm(
            @Param("filmId") Long filmId,
            @Param("today") LocalDate today
    );

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findByScreeningDateOrderByStartTimeAsc(LocalDate screeningDate);

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findByFilmIdAndScreeningDateOrderByStartTimeAsc(
            Long filmId,
            LocalDate screeningDate
    );

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findByFilmIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
            Long filmId,
            LocalDate startDate,
            LocalDate endDate
    );

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findByFilmIdAndScreenCinemaIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
            Long filmId,
            Long cinemaId,
            LocalDate startDate,
            LocalDate endDate
    );

    @EntityGraph(attributePaths = {"film", "screen", "screen.cinema"})
    List<Screening> findByScreenIdAndScreeningDateOrderByStartTimeAsc(
            Long screenId,
            LocalDate screeningDate
    );

    /**
     * Important: admin scheduling must filter by real screen.id, not screen number.
     * Different cinemas can both have "Screen 1", but their screen IDs are different.
     */
    @Query("""
            select distinct s
            from Screening s
            join fetch s.film f
            join fetch s.screen sc
            join fetch sc.cinema c
            where sc.id = :screenId
              and s.screeningDate between :startDate and :endDate
            order by s.screeningDate asc, s.startTime asc, s.id asc
            """)
    List<Screening> findByScreenIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
            @Param("screenId") Long screenId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select distinct s
            from Screening s
            join fetch s.film f
            join fetch s.screen sc
            join fetch sc.cinema c
            where f.id = :filmId
              and c.id = :cinemaId
              and s.screeningDate = :screeningDate
            order by s.startTime asc, sc.screenNumber asc, s.id asc
            """)
    List<Screening> findShowtimesForFilmCinemaAndDate(
            @Param("filmId") Long filmId,
            @Param("cinemaId") Long cinemaId,
            @Param("screeningDate") LocalDate screeningDate
    );

    boolean existsByScreen_IdAndScreeningDateAndStartTime(
            Long screenId,
            LocalDate screeningDate,
            LocalTime startTime
    );

    @Query("""
            select count(s) > 0
            from Screening s
            where s.screen.id = :screenId
              and s.screeningDate = :screeningDate
              and (:excludeId is null or s.id <> :excludeId)
              and (:startTime < s.endTime and :endTime > s.startTime)
            """)
    boolean existsOverlappingScreening(
            @Param("screenId") Long screenId,
            @Param("screeningDate") LocalDate screeningDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId
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

    @Modifying
    @Query(
            value = """
                    DELETE FROM screenings
                    WHERE (
                            screening_date < :today
                            OR (screening_date = :today AND end_time < :currentTime)
                          )
                      AND id NOT IN (
                          SELECT screening_id
                          FROM bookings
                      )
                    """,
            nativeQuery = true
    )
    int deleteExpiredUnbookedScreenings(
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime
    );
}
