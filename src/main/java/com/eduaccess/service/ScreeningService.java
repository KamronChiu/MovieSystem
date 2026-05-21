package com.eduaccess.service;

import com.eduaccess.domain.Screening;
import com.eduaccess.repository.ScreeningRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

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
}