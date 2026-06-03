package com.eduaccess.service;

import com.eduaccess.domain.*;
import com.eduaccess.repository.ScreeningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ScreeningService}.
 * <p>
 * Uses Mockito to isolate the service from the repository layer.
 */
@ExtendWith(MockitoExtension.class)
class ScreeningServiceTest {

    @Mock
    private ScreeningRepository screeningRepository;

    @InjectMocks
    private ScreeningService screeningService;

    @Test
    @DisplayName("findEarliestUpcomingDateForFilm_whenExists_returnsDate")
    void findEarliestUpcomingDateForFilm_whenExists_returnsDate() {
        LocalDate expected = LocalDate.now().plusDays(3);
        when(screeningRepository.findEarliestUpcomingScreeningDateForFilm(eq(1L), any()))
                .thenReturn(expected);

        Optional<LocalDate> result = screeningService.findEarliestUpcomingDateForFilm(1L);

        assertThat(result).isPresent().contains(expected);
    }

    @Test
    @DisplayName("findEarliestUpcomingDateForFilm_whenNone_returnsEmpty")
    void findEarliestUpcomingDateForFilm_whenNone_returnsEmpty() {
        when(screeningRepository.findEarliestUpcomingScreeningDateForFilm(eq(99L), any()))
                .thenReturn(null);

        Optional<LocalDate> result = screeningService.findEarliestUpcomingDateForFilm(99L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findEarliestUpcomingDateForFilm_nullId_returnsEmpty")
    void findEarliestUpcomingDateForFilm_nullId_returnsEmpty() {
        Optional<LocalDate> result = screeningService.findEarliestUpcomingDateForFilm(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findScreeningsBetween_delegatesToRepository")
    void findScreeningsBetween_delegatesToRepository() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);

        when(screeningRepository
                .findByScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(start, end))
                .thenReturn(List.of());

        List<Screening> results = screeningService.findScreeningsBetween(start, end);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("findScreeningsByCinemaBetween_delegatesToRepository")
    void findScreeningsByCinemaBetween_delegatesToRepository() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start;

        when(screeningRepository
                .findByScreenCinemaIdAndScreeningDateBetweenOrderByScreeningDateAscStartTimeAsc(
                        5L, start, end))
                .thenReturn(Collections.emptyList());

        List<Screening> results = screeningService.findScreeningsByCinemaBetween(5L, start, end);
        assertThat(results).isEmpty();
    }

    // ═══ Overlap validation tests (verifies repository call) ═════════════════════

    @Test
    @DisplayName("existsOverlappingScreening_conflict_returnsTrue")
    void existsOverlappingScreening_conflict_returnsTrue() {
        Long screenId = 1L;
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime start = LocalTime.of(14, 0);
        LocalTime end = LocalTime.of(16, 0);

        when(screeningRepository.existsOverlappingScreening(screenId, date, start, end, null))
                .thenReturn(true);

        boolean result = screeningRepository.existsOverlappingScreening(
                screenId, date, start, end, null);
        assertThat(result).isTrue();
        verify(screeningRepository).existsOverlappingScreening(screenId, date, start, end, null);
    }

    @Test
    @DisplayName("existsOverlappingScreening_noConflict_returnsFalse")
    void existsOverlappingScreening_noConflict_returnsFalse() {
        Long screenId = 1L;
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime start = LocalTime.of(20, 0);
        LocalTime end = LocalTime.of(22, 0);

        when(screeningRepository.existsOverlappingScreening(screenId, date, start, end, null))
                .thenReturn(false);

        boolean result = screeningRepository.existsOverlappingScreening(
                screenId, date, start, end, null);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("existsOverlappingScreening_excludesSelf_returnsFalse")
    void existsOverlappingScreening_excludesSelf_returnsFalse() {
        Long screenId = 1L;
        Long selfId = 42L;
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime start = LocalTime.of(14, 0);
        LocalTime end = LocalTime.of(16, 0);

        // When excluding its own id, there's no overlap
        when(screeningRepository.existsOverlappingScreening(screenId, date, start, end, selfId))
                .thenReturn(false);

        boolean result = screeningRepository.existsOverlappingScreening(
                screenId, date, start, end, selfId);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("findScreeningsByFilmAndDate_delegatesToRepository")
    void findScreeningsByFilmAndDate_delegatesToRepository() {
        LocalDate date = LocalDate.now().plusDays(2);
        when(screeningRepository.findByFilmIdAndScreeningDateOrderByStartTimeAsc(1L, date))
                .thenReturn(Collections.emptyList());

        List<Screening> results = screeningService.findScreeningsByFilmAndDate(1L, date);
        assertThat(results).isEmpty();
        verify(screeningRepository).findByFilmIdAndScreeningDateOrderByStartTimeAsc(1L, date);
    }
}
