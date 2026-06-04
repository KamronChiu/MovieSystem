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
 * IT_022 — Integration test verifying the film listing filter repository queries.
 * Uses the ScreeningRepository's city+genre+date combination queries.
 */
@DataJpaTest
class FilmListingRepositoryIT {

    @Autowired private TestEntityManager em;
    @Autowired private ScreeningRepository screeningRepository;

    private Cinema londonCinema;
    private Cinema manchesterCinema;
    private Film actionFilm;
    private Film dramaFilm;

    @BeforeEach
    void setUp() {
        londonCinema = new Cinema("Odeon", "London", "1 London St");
        em.persist(londonCinema);
        manchesterCinema = new Cinema("Vue", "Manchester", "2 Manchester Rd");
        em.persist(manchesterCinema);

        Screen londonScreen = new Screen(londonCinema, 1, 100, HallType.REGULAR);
        em.persist(londonScreen);
        Screen manchesterScreen = new Screen(manchesterCinema, 1, 80, HallType.REGULAR);
        em.persist(manchesterScreen);

        actionFilm = new Film("Action Movie", "Desc", "Actor", "Action", "12A", 120);
        em.persist(actionFilm);
        dramaFilm = new Film("Drama Movie", "Desc", "Actress", "Drama", "PG", 90);
        em.persist(dramaFilm);

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // London action screening
        em.persist(new Screening(actionFilm, londonScreen, tomorrow, LocalTime.of(14, 0)));
        // London drama screening
        em.persist(new Screening(dramaFilm, londonScreen, tomorrow, LocalTime.of(18, 0)));
        // Manchester action screening
        em.persist(new Screening(actionFilm, manchesterScreen, tomorrow, LocalTime.of(20, 0)));

        em.flush();
    }

    @Test
    @DisplayName("IT_022 filterByCityGenreDate_returnsMatchingScreenings")
    void filterByCityGenreDate_returnsMatchingScreenings() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Filter by London cinema only
        List<Screening> londonScreenings = screeningRepository
                .findByScreenCinemaIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                        londonCinema.getId(), tomorrow, tomorrow);

        assertThat(londonScreenings).hasSize(2);
        assertThat(londonScreenings).allMatch(s ->
                s.getScreen().getCinema().getCity().equals("London"));

        // Filter by film (Action) in London
        List<Screening> actionInLondon = screeningRepository
                .findByFilmIdAndScreenCinemaIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                        actionFilm.getId(), londonCinema.getId(), tomorrow, tomorrow);

        assertThat(actionInLondon).hasSize(1);
        assertThat(actionInLondon.get(0).getFilm().getGenre()).isEqualTo("Action");
        assertThat(actionInLondon.get(0).getScreen().getCinema().getCity()).isEqualTo("London");
    }
}
