package com.eduaccess.service;

import com.eduaccess.domain.Film;
import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.repository.ScreenRepository;
import com.eduaccess.repository.ScreeningRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Service
public class SchedulingService {

    private static final int MAX_DAILY_SHOWS_PER_SCREEN = 4;

    private final ScreeningRepository screeningRepository;
    private final FilmRepository filmRepository;
    private final ScreenRepository screenRepository;

    public SchedulingService(
            ScreeningRepository screeningRepository,
            FilmRepository filmRepository,
            ScreenRepository screenRepository
    ) {
        this.screeningRepository = screeningRepository;
        this.filmRepository = filmRepository;
        this.screenRepository = screenRepository;
    }

    @Transactional(readOnly = true)
    public List<Screening> findAllScreeningsForAdmin() {
        return screeningRepository.findAllByOrderByScreeningDateAscStartTimeAsc();
    }

    @Transactional
    public Screening createScreening(
            Long filmId,
            Long screenId,
            LocalDate screeningDate,
            LocalTime startTime
    ) {
        return createScreening(filmId, screenId, screeningDate, startTime, ScreeningType.REGULAR_2D);
    }

    @Transactional
    public Screening createScreening(
            Long filmId,
            Long screenId,
            LocalDate screeningDate,
            LocalTime startTime,
            ScreeningType screeningType
    ) {
        Film film = filmRepository.findById(filmId)
                .orElseThrow(() -> new IllegalArgumentException("Film was not found."));

        Screen screen = screenRepository.findById(screenId)
                .orElseThrow(() -> new IllegalArgumentException("Screen was not found."));

        Screening screening = new Screening(film, screen, screeningDate, startTime, screeningType);

        validateScreening(screening, null);

        return screeningRepository.save(screening);
    }

    @Transactional
    public Screening updateScreening(
            Long screeningId,
            Long filmId,
            Long screenId,
            LocalDate screeningDate,
            LocalTime startTime
    ) {
        return updateScreening(
                screeningId,
                filmId,
                screenId,
                screeningDate,
                startTime,
                ScreeningType.REGULAR_2D
        );
    }

    @Transactional
    public Screening updateScreening(
            Long screeningId,
            Long filmId,
            Long screenId,
            LocalDate screeningDate,
            LocalTime startTime,
            ScreeningType screeningType
    ) {
        Screening screening = screeningRepository.findById(screeningId)
                .orElseThrow(() -> new IllegalArgumentException("Screening was not found."));

        if (screeningRepository.hasAnyBookingsForScreening(screeningId)) {
            throw new IllegalStateException(
                    "This screening already has bookings, so it cannot be changed. Remove or cancel related bookings first."
            );
        }

        Film film = filmRepository.findById(filmId)
                .orElseThrow(() -> new IllegalArgumentException("Film was not found."));

        Screen screen = screenRepository.findById(screenId)
                .orElseThrow(() -> new IllegalArgumentException("Screen was not found."));

        screening.setFilm(film);
        screening.setScreen(screen);
        screening.setScreeningDate(screeningDate);
        screening.setStartTime(startTime);
        screening.setEndTime(startTime.plusMinutes(film.getDurationMinutes()));
        screening.setScreeningType(screeningType);

        validateScreening(screening, screeningId);

        return screeningRepository.save(screening);
    }

    @Transactional
    public void deleteScreening(Long screeningId) {
        Screening screening = screeningRepository.findById(screeningId)
                .orElseThrow(() -> new IllegalArgumentException("Screening was not found."));

        if (screeningRepository.hasAnyBookingsForScreening(screeningId)) {
            throw new IllegalStateException(
                    "This screening already has bookings, so it cannot be deleted."
            );
        }

        screeningRepository.delete(screening);
    }

    @Transactional
    public int cleanupExpiredUnbookedScreenings() {
        return screeningRepository.deleteExpiredUnbookedScreenings(LocalDate.now());
    }

    private void validateScreening(Screening screening, Long currentScreeningId) {
        validateRequiredFields(screening);
        validateDateAndTime(screening);
        validateDailyShowLimit(screening, currentScreeningId);
        validateNoTimeOverlap(screening, currentScreeningId);
    }

    private void validateRequiredFields(Screening screening) {
        if (screening.getFilm() == null) {
            throw new IllegalArgumentException("Film is required.");
        }

        if (screening.getScreen() == null) {
            throw new IllegalArgumentException("Screen is required.");
        }

        if (screening.getScreeningDate() == null) {
            throw new IllegalArgumentException("Screening date is required.");
        }

        if (screening.getStartTime() == null) {
            throw new IllegalArgumentException("Start time is required.");
        }
    }

    private void validateDateAndTime(Screening screening) {
        LocalDate today = LocalDate.now();

        if (screening.getScreeningDate().isBefore(today)) {
            throw new IllegalArgumentException("Cannot schedule a screening in the past.");
        }

        boolean beforeReleaseDate =
                screening.getFilm().getReleaseDate() != null
                        && screening.getScreeningDate().isBefore(screening.getFilm().getReleaseDate());

        boolean advancePreview = !screening.getScreeningType().isRegular();

        if (beforeReleaseDate && !advancePreview) {
            throw new IllegalArgumentException(
                    "Cannot schedule a regular screening before the release date. "
                            + "Use ADVANCE_PREVIEW for early access screenings."
            );
        }

        if (screening.getEndTime() == null) {
            throw new IllegalArgumentException("End time could not be calculated.");
        }

        if (!screening.getEndTime().isAfter(screening.getStartTime())) {
            throw new IllegalArgumentException("The screening must finish before midnight.");
        }

        if (screening.getStartTime().isBefore(LocalTime.of(8, 0))) {
            throw new IllegalArgumentException("Screenings cannot start before 08:00.");
        }

        if (screening.getEndTime().isAfter(LocalTime.of(23, 59))) {
            throw new IllegalArgumentException("Screenings must finish before midnight.");
        }
    }

    private void validateDailyShowLimit(Screening screening, Long currentScreeningId) {
        List<Screening> sameDayScreenings =
                screeningRepository.findByScreenIdAndScreeningDateOrderByStartTimeAsc(
                        screening.getScreen().getId(),
                        screening.getScreeningDate()
                );

        long count = sameDayScreenings.stream()
                .filter(existing -> !Objects.equals(existing.getId(), currentScreeningId))
                .count();

        if (count >= MAX_DAILY_SHOWS_PER_SCREEN) {
            throw new IllegalStateException(
                    "Each screen can only run up to four shows per day."
            );
        }
    }

    private void validateNoTimeOverlap(Screening screening, Long currentScreeningId) {
        List<Screening> sameDayScreenings =
                screeningRepository.findByScreenIdAndScreeningDateOrderByStartTimeAsc(
                        screening.getScreen().getId(),
                        screening.getScreeningDate()
                );

        for (Screening existing : sameDayScreenings) {
            if (Objects.equals(existing.getId(), currentScreeningId)) {
                continue;
            }

            boolean overlap =
                    screening.getStartTime().isBefore(existing.getEndTime())
                            && screening.getEndTime().isAfter(existing.getStartTime());

            if (overlap) {
                throw new IllegalStateException(
                        "This screening overlaps with an existing screening: "
                                + existing.getFilm().getTitle()
                                + " "
                                + existing.getStartTime()
                                + " - "
                                + existing.getEndTime()
                );
            }
        }
    }
}
