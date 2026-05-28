package com.eduaccess.service;

import com.eduaccess.domain.AuditAction;
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
    private final AuditLogService auditLogService;

    public SchedulingService(
            ScreeningRepository screeningRepository,
            FilmRepository filmRepository,
            ScreenRepository screenRepository,
            AuditLogService auditLogService
    ) {
        this.screeningRepository = screeningRepository;
        this.filmRepository = filmRepository;
        this.screenRepository = screenRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Screening> findAllScreeningsForAdmin() {
        return screeningRepository.findAllByOrderByScreeningDateAscStartTimeAsc();
    }

    /**
     * Loads screenings only for the selected physical screen ID.
     * This prevents "Screen 1" in one cinema from being treated as the same
     * as "Screen 1" in another cinema.
     *
     * This method deliberately uses the already-stable date-range repository
     * query and filters by screen.id in Java, instead of depending on a long
     * Spring Data method name. This avoids merge conflicts where the repository
     * method was missing after pulling team code.
     */
    @Transactional(readOnly = true)
    public List<Screening> findScreeningsByScreenBetween(
            Long screenId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (screenId == null || startDate == null || endDate == null) {
            return List.of();
        }

        return screeningRepository.findByScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                        startDate,
                        endDate
                )
                .stream()
                .filter(screening -> screening.getScreen() != null)
                .filter(screening -> Objects.equals(screening.getScreen().getId(), screenId))
                .toList();
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

        Screening screening = new Screening(film, screen, screeningDate, startTime, normaliseScreeningType(screeningType));

        validateScreening(screening, null);

        Screening savedScreening = screeningRepository.save(screening);
        auditLogService.record(
                AuditAction.SCREENING_CREATED,
                "Screening",
                savedScreening.getId(),
                null,
                savedScreening.getFilm().getTitle(),
                savedScreening.getScreen().getCinema().getName(),
                null,
                "Screening created: " + savedScreening.getFilm().getTitle(),
                screeningDetails(savedScreening)
        );
        return savedScreening;
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
        screening.setScreeningType(normaliseScreeningType(screeningType));

        validateScreening(screening, screeningId);

        Screening savedScreening = screeningRepository.save(screening);
        auditLogService.record(
                AuditAction.SCREENING_UPDATED,
                "Screening",
                savedScreening.getId(),
                null,
                savedScreening.getFilm().getTitle(),
                savedScreening.getScreen().getCinema().getName(),
                null,
                "Screening updated: " + savedScreening.getFilm().getTitle(),
                screeningDetails(savedScreening)
        );
        return savedScreening;
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

        auditLogService.record(
                AuditAction.SCREENING_DELETED,
                "Screening",
                screening.getId(),
                null,
                screening.getFilm().getTitle(),
                screening.getScreen().getCinema().getName(),
                null,
                "Screening deleted: " + screening.getFilm().getTitle(),
                screeningDetails(screening)
        );

        screeningRepository.delete(screening);
    }

    @Transactional
    public int cleanupExpiredUnbookedScreenings() {
        return screeningRepository.deleteExpiredUnbookedScreenings(
                LocalDate.now(),
                LocalTime.now()
        );
    }

    private ScreeningType normaliseScreeningType(ScreeningType screeningType) {
        return screeningType == null ? ScreeningType.REGULAR_2D : screeningType;
    }

    private String screeningDetails(Screening screening) {
        if (screening == null) {
            return "-";
        }
        return "Film: " + screening.getFilm().getTitle()
                + "; Cinema: " + screening.getScreen().getCinema().getName()
                + "; Screen: " + screening.getScreen().getScreenNumber()
                + "; Date: " + screening.getScreeningDate()
                + "; Time: " + screening.getStartTime() + " - " + screening.getEndTime()
                + "; Type: " + screening.getScreeningType();
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
                            + "Use an advance preview screening type for early access screenings."
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
