package com.eduaccess.service;

import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.HallType;
import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Seat;
import com.eduaccess.domain.SeatType;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.repository.ScreenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CinemaService {

    private static final int MIN_SCREEN_CAPACITY = 30;
    private static final int MAX_SCREEN_CAPACITY = 120;
    private static final int MAX_SCREENS_PER_CINEMA = 6;
    private static final int REGULAR_SEATS_PER_ROW = 10;
    private static final int IMAX_SEATS_PER_ROW = 8;
    private static final int PREMIUM_SEATS_PER_ROW = 6;

    private final CinemaRepository cinemaRepository;
    private final ScreenRepository screenRepository;

    public CinemaService(CinemaRepository cinemaRepository, ScreenRepository screenRepository) {
        this.cinemaRepository = cinemaRepository;
        this.screenRepository = screenRepository;
    }

    @Transactional(readOnly = true)
    public List<Cinema> findAllCinemas() {
        return cinemaRepository.findAll()
                .stream()
                .sorted((a, b) -> {
                    int cityCompare = safe(a.getCity()).compareToIgnoreCase(safe(b.getCity()));
                    if (cityCompare != 0) {
                        return cityCompare;
                    }
                    return safe(a.getName()).compareToIgnoreCase(safe(b.getName()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Screen> findScreensForCinema(Long cinemaId) {
        if (cinemaId == null) {
            return List.of();
        }
        return screenRepository.findByCinemaIdOrderByScreenNumberAsc(cinemaId);
    }

    @Transactional
    public Cinema createCinema(String city, String name, String address, List<Integer> screenCapacities) {
        String cleanedCity = clean(city, "City");
        String cleanedName = clean(name, "Cinema name");
        String cleanedAddress = clean(address, "Address");

        validateScreenCapacities(screenCapacities);

        if (cinemaRepository.existsByCityIgnoreCaseAndNameIgnoreCase(cleanedCity, cleanedName)) {
            throw new IllegalArgumentException("A cinema with this name already exists in " + cleanedCity + ".");
        }

        Cinema cinema = new Cinema(cleanedName, cleanedCity, cleanedAddress);

        for (int i = 0; i < screenCapacities.size(); i++) {
            int screenNumber = i + 1;
            int capacity = screenCapacities.get(i);
            Screen screen = new Screen(cinema, screenNumber, capacity);
            addGeneratedSeats(screen, capacity);
            cinema.addScreen(screen);
        }

        return cinemaRepository.save(cinema);
    }

    @Transactional
    public Screen addScreen(Long cinemaId, int capacity, HallType hallType) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new IllegalArgumentException("Cinema was not found."));

        List<Screen> existingScreens = screenRepository.findByCinemaIdOrderByScreenNumberAsc(cinemaId);

        if (existingScreens.size() >= MAX_SCREENS_PER_CINEMA) {
            throw new IllegalStateException("Each cinema can have up to 6 screens only.");
        }

        validateCapacity(capacity, "New screen capacity");

        int nextScreenNumber = existingScreens.stream()
                .mapToInt(Screen::getScreenNumber)
                .max()
                .orElse(0) + 1;

        HallType effectiveHallType = hallType == null ? HallType.REGULAR : hallType;
        Screen screen = new Screen(cinema, nextScreenNumber, capacity, effectiveHallType);
        addGeneratedSeats(screen, capacity, effectiveHallType);
        cinema.addScreen(screen);
        cinemaRepository.save(cinema);
        return screen;
    }

    @Transactional
    public Screen addScreen(Long cinemaId, int capacity) {
        return addScreen(cinemaId, capacity, HallType.REGULAR);
    }

    private void validateScreenCapacities(List<Integer> screenCapacities) {
        if (screenCapacities == null || screenCapacities.isEmpty()) {
            throw new IllegalArgumentException("At least one screen is required.");
        }

        if (screenCapacities.size() > MAX_SCREENS_PER_CINEMA) {
            throw new IllegalArgumentException("Each cinema can have up to 6 screens only.");
        }

        for (int i = 0; i < screenCapacities.size(); i++) {
            validateCapacity(screenCapacities.get(i), "Screen " + (i + 1) + " capacity");
        }
    }

    private void validateCapacity(Integer capacity, String label) {
        if (capacity == null) {
            throw new IllegalArgumentException(label + " is required.");
        }

        if (capacity < MIN_SCREEN_CAPACITY || capacity > MAX_SCREEN_CAPACITY) {
            throw new IllegalArgumentException(label + " must be between 30 and 120 seats.");
        }
    }

    private void addGeneratedSeats(Screen screen, int capacity, HallType hallType) {
        List<Seat> seats = new ArrayList<>();
        int seatsPerRow = getSeatsPerRow(hallType);

        for (int i = 0; i < capacity; i++) {
            String seatNumber = buildSeatLabel(i, seatsPerRow);
            SeatType seatType = getSeatTypeForPosition(i, capacity, hallType);
            seats.add(new Seat(screen, seatNumber, seatType));
        }

        seats.forEach(screen::addSeat);
    }

    private void addGeneratedSeats(Screen screen, int capacity) {
        addGeneratedSeats(screen, capacity, HallType.REGULAR);
    }

    private int getSeatsPerRow(HallType hallType) {
        return switch (hallType) {
            case IMAX -> IMAX_SEATS_PER_ROW;
            case PREMIUM -> PREMIUM_SEATS_PER_ROW;
            case REGULAR -> REGULAR_SEATS_PER_ROW;
        };
    }

    private SeatType getSeatTypeForPosition(int index, int capacity, HallType hallType) {
        int seatsPerRow = getSeatsPerRow(hallType);
        int totalRows = (int) Math.ceil((double) capacity / seatsPerRow);
        int row = index / seatsPerRow;
        int seatInRow = index % seatsPerRow;

        // 中间位置：每排的中间3-4个座位
        int middleSeatStart = Math.max(0, (seatsPerRow / 2) - 2);
        int middleSeatEnd = Math.min(seatsPerRow - 1, (seatsPerRow / 2) + 1);

        // 中间区域：排除前2排和后2排
        boolean isMiddleRow = row >= 2 && row <= totalRows - 3;
        boolean isCenterSeat = seatInRow >= middleSeatStart && seatInRow <= middleSeatEnd;

        if (isMiddleRow && isCenterSeat) {
            return SeatType.CENTER;
        } else if (isMiddleRow) {
            return SeatType.PREMIUM;
        } else {
            return SeatType.STANDARD;
        }
    }

    private String buildSeatLabel(int index, int seatsPerRow) {
        char row = (char) ('A' + (index / seatsPerRow));
        int seatNumber = (index % seatsPerRow) + 1;
        return row + String.valueOf(seatNumber);
    }

    private String clean(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
