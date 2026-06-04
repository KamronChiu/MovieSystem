package com.eduaccess.repository;

import com.eduaccess.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_001 / IT_002 / IT_003 — Integration tests for {@link ScreeningRepository}.
 * <p>
 * Uses {@code @DataJpaTest} with in-memory H2, verifying that custom JPQL
 * queries and derived queries behave correctly against a real database schema.
 */
@DataJpaTest
class ScreeningRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ScreeningRepository screeningRepository;

    private Film film;
    private Cinema cinema;
    private Screen screen;

    @BeforeEach
    void setUp() {
        cinema = new Cinema("Test Cinema", "London", "1 Test St");
        em.persist(cinema);

        screen = new Screen(cinema, 1, 100, HallType.REGULAR);
        em.persist(screen);

        film = new Film("Test Film", "Description", "Actor A",
                "Action", "PG", 120);
        em.persist(film);

        em.flush();
    }

    @Test
    @DisplayName("IT_001 findByScreeningDateBetween_returnsScreeningsInRange")
    void findByScreeningDateBetween_returnsScreeningsInRange() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate dayAfter = LocalDate.now().plusDays(2);
        LocalDate nextWeek = LocalDate.now().plusDays(7);

        Screening s1 = new Screening(film, screen, tomorrow, LocalTime.of(10, 0));
        Screening s2 = new Screening(film, screen, dayAfter, LocalTime.of(14, 0));
        Screening s3 = new Screening(film, screen, nextWeek, LocalTime.of(20, 0));
        em.persist(s1);
        em.persist(s2);
        em.persist(s3);
        em.flush();

        List<Screening> results = screeningRepository
                .findByScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                        tomorrow, dayAfter);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getScreeningDate()).isEqualTo(tomorrow);
        assertThat(results.get(1).getScreeningDate()).isEqualTo(dayAfter);
    }

    @Test
    @DisplayName("IT_002 findEarliestUpcomingScreeningDateForFilm_returnsCorrectDate")
    void findEarliestUpcomingScreeningDateForFilm_returnsCorrectDate() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate dayAfter = today.plusDays(2);

        // Past screening — should be excluded
        Screening past = new Screening(film, screen, today.minusDays(1), LocalTime.of(10, 0));
        em.persist(past);

        // Future screenings
        Screening future1 = new Screening(film, screen, dayAfter, LocalTime.of(10, 0));
        Screening future2 = new Screening(film, screen, tomorrow, LocalTime.of(14, 0));
        em.persist(future1);
        em.persist(future2);
        em.flush();

        LocalDate earliest = screeningRepository
                .findEarliestUpcomingScreeningDateForFilm(film.getId(), today);

        assertThat(earliest).isEqualTo(tomorrow);
    }

    @Test
    @DisplayName("IT_003 findByScreenCinemaIdAndScreeningDateBetween_filtersByCinema")
    void findByScreenCinemaIdAndScreeningDateBetween_filtersByCinema() {
        // Second cinema with its own screen and screening
        Cinema cinema2 = new Cinema("Other Cinema", "Manchester", "2 Other St");
        em.persist(cinema2);
        Screen screen2 = new Screen(cinema2, 1, 80, HallType.REGULAR);
        em.persist(screen2);

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        Screening s1 = new Screening(film, screen, tomorrow, LocalTime.of(10, 0));
        Screening s2 = new Screening(film, screen2, tomorrow, LocalTime.of(14, 0));
        em.persist(s1);
        em.persist(s2);
        em.flush();

        List<Screening> results = screeningRepository
                .findByScreenCinemaIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                        cinema.getId(), tomorrow, tomorrow);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getScreen().getCinema().getName()).isEqualTo("Test Cinema");
    }

    @Test
    @DisplayName("existsByFilmId_whenScreeningsExist_returnsTrue")
    void existsByFilmId_whenScreeningsExist_returnsTrue() {
        Screening s = new Screening(film, screen, LocalDate.now().plusDays(1), LocalTime.of(10, 0));
        em.persist(s);
        em.flush();

        assertThat(screeningRepository.existsByFilmId(film.getId())).isTrue();
    }

    @Test
    @DisplayName("existsByFilmId_whenNoScreenings_returnsFalse")
    void existsByFilmId_whenNoScreenings_returnsFalse() {
        Film otherFilm = new Film("Other Film", "Desc", "B", "Comedy", "12A", 90);
        em.persist(otherFilm);
        em.flush();

        assertThat(screeningRepository.existsByFilmId(otherFilm.getId())).isFalse();
    }

    @Test
    @DisplayName("existsOverlappingScreening_detectsConflict")
    void existsOverlappingScreening_detectsConflict() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Screening existing = new Screening(film, screen, tomorrow, LocalTime.of(10, 0));
        em.persist(existing);
        em.flush();

        // Overlapping: 10:30 to 12:30 overlaps with 10:00-12:00
        boolean overlaps = screeningRepository.existsOverlappingScreening(
                screen.getId(), tomorrow,
                LocalTime.of(10, 30), LocalTime.of(12, 30),
                null);

        assertThat(overlaps).isTrue();
    }

    @Test
    @DisplayName("existsOverlappingScreening_noConflictOutsideRange")
    void existsOverlappingScreening_noConflictOutsideRange() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Screening existing = new Screening(film, screen, tomorrow, LocalTime.of(10, 0));
        em.persist(existing);
        em.flush();

        // Non-overlapping: 14:00-16:00 vs 10:00-12:00
        boolean overlaps = screeningRepository.existsOverlappingScreening(
                screen.getId(), tomorrow,
                LocalTime.of(14, 0), LocalTime.of(16, 0),
                null);

        assertThat(overlaps).isFalse();
    }
}
