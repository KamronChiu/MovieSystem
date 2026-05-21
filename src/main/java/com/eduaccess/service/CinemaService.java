package com.eduaccess.service;

import com.eduaccess.domain.Cinema;
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

    private static final int MIN_SCREEN_CAPACITY = 50;
    private static final int MAX_SCREEN_CAPACITY = 120;
    private static final int MAX_SCREENS_PER_CINEMA = 6;
    private static final int SEATS_PER_ROW = 10;

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
    public Screen addScreen(Long cinemaId, int capacity) {
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

        Screen screen = new Screen(cinema, nextScreenNumber, capacity);
        addGeneratedSeats(screen, capacity);
        cinema.addScreen(screen);
        cinemaRepository.save(cinema);
        return screen;
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
            throw new IllegalArgumentException(label + " must be between 50 and 120 seats.");
        }
    }

    private void addGeneratedSeats(Screen screen, int capacity) {
        List<Seat> seats = new ArrayList<>();

        for (int i = 0; i < capacity; i++) {
            String seatNumber = buildSeatLabel(i);
            SeatType seatType = i < Math.round(capacity * 0.7)
                    ? SeatType.LOWER_HALL
                    : SeatType.UPPER_GALLERY;
            seats.add(new Seat(screen, seatNumber, seatType));
        }

        seats.forEach(screen::addSeat);
    }

    private String buildSeatLabel(int index) {
        char row = (char) ('A' + (index / SEATS_PER_ROW));
        int seatNumber = (index % SEATS_PER_ROW) + 1;
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
