package com.eduaccess.service;

import com.eduaccess.domain.Screening;
import com.eduaccess.repository.ScreeningRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ScreeningService {

    private final ScreeningRepository screeningRepository;

    public ScreeningService(ScreeningRepository screeningRepository) {
        this.screeningRepository = screeningRepository;
    }

    public List<Screening> findScreeningsBetween(LocalDate startDate, LocalDate endDate) {
        return screeningRepository.findByScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                startDate,
                endDate
        );
    }

    public List<Screening> findScreeningsByCinemaBetween(
            Long cinemaId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return screeningRepository
                .findByScreenCinemaIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                        cinemaId,
                        startDate,
                        endDate
                );
    }

    /**
     * Finds the soonest future screening date (today or later) for the given film.
     * Returns an empty Optional when the film has no upcoming screenings.
     */
    public Optional<LocalDate> findEarliestUpcomingDateForFilm(Long filmId) {
        if (filmId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                screeningRepository.findEarliestUpcomingScreeningDateForFilm(filmId, LocalDate.now())
        );
    }

    public List<Screening> findScreeningsOn(LocalDate screeningDate) {
        return screeningRepository.findByScreeningDateOrderByStartTimeAsc(screeningDate);
    }

    public List<Screening> findScreeningsByFilmAndDate(Long filmId, LocalDate screeningDate) {
        return screeningRepository.findByFilmIdAndScreeningDateOrderByStartTimeAsc(filmId, screeningDate);
    }

    public List<Screening> findScreeningsByFilmBetween(
            Long filmId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return screeningRepository
                .findByFilmIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                        filmId,
                        startDate,
                        endDate
                );
    }

    public List<Screening> findScreeningsByFilmCinemaBetween(
            Long filmId,
            Long cinemaId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return screeningRepository
                .findByFilmIdAndScreenCinemaIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                        filmId,
                        cinemaId,
                        startDate,
                        endDate
                );
    }
}
