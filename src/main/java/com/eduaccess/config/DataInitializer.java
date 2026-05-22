package com.eduaccess.config;

import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.Film;
import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.domain.Seat;
import com.eduaccess.domain.SeatType;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.repository.ScreenRepository;
import com.eduaccess.repository.ScreeningRepository;
import com.eduaccess.repository.SeatRepository;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class DataInitializer {

    private static final int SEATS_PER_ROW = 10;

    @Bean
    CommandLineRunner maintainSeedData(
            CinemaRepository cinemaRepository,
            FilmRepository filmRepository,
            ScreenRepository screenRepository,
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager
    ) {
        return args -> {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

            transactionTemplate.executeWithoutResult(status -> {
                int deletedScreenings = deleteExpiredUnbookedScreenings(entityManager);

                seedCinemasIfEmpty(cinemaRepository);
                seedFilmsIfEmpty(filmRepository);

                ensureSeatsExistForAllScreens(screenRepository, seatRepository);

                seedScreeningsIfEmpty(
                        filmRepository,
                        screenRepository,
                        screeningRepository
                );

                System.out.println("HCBS data maintenance completed.");
                System.out.println("Expired unbooked screenings removed: " + deletedScreenings);
                System.out.println("Films in database: " + filmRepository.count());
                System.out.println("Cinemas in database: " + cinemaRepository.count());
                System.out.println("Screens in database: " + screenRepository.count());
                System.out.println("Screenings in database: " + screeningRepository.count());
            });
        };
    }

    private int deleteExpiredUnbookedScreenings(EntityManager entityManager) {
        /*
         * Safe cleanup rule:
         * - Delete only screenings before today.
         * - Do not delete screenings that already have bookings.
         *
         * This preserves booking history and avoids foreign-key errors.
         */
        return entityManager.createNativeQuery("""
                DELETE FROM screenings s
                WHERE s.screening_date < CURRENT_DATE
                  AND NOT EXISTS (
                      SELECT 1
                      FROM bookings b
                      WHERE b.screening_id = s.id
                  )
                """).executeUpdate();
    }

    private void seedCinemasIfEmpty(CinemaRepository cinemaRepository) {
        if (cinemaRepository.count() > 0) {
            return;
        }

        List<Cinema> cinemas = new ArrayList<>();

        Cinema londonCentral = new Cinema(
                "Horizon London Central",
                "London",
                "12 King Street, London"
        );
        londonCentral.addScreen(new Screen(londonCentral, 1, 80));
        londonCentral.addScreen(new Screen(londonCentral, 2, 60));
        londonCentral.addScreen(new Screen(londonCentral, 3, 100));
        cinemas.add(londonCentral);

        Cinema londonRiverside = new Cinema(
                "Horizon London Riverside",
                "London",
                "45 River Road, London"
        );
        londonRiverside.addScreen(new Screen(londonRiverside, 1, 90));
        londonRiverside.addScreen(new Screen(londonRiverside, 2, 70));
        cinemas.add(londonRiverside);

        Cinema birminghamCity = new Cinema(
                "Horizon Birmingham City",
                "Birmingham",
                "8 New Street, Birmingham"
        );
        birminghamCity.addScreen(new Screen(birminghamCity, 1, 70));
        birminghamCity.addScreen(new Screen(birminghamCity, 2, 90));
        birminghamCity.addScreen(new Screen(birminghamCity, 3, 110));
        cinemas.add(birminghamCity);

        Cinema birminghamSouth = new Cinema(
                "Horizon Birmingham South",
                "Birmingham",
                "22 Park Lane, Birmingham"
        );
        birminghamSouth.addScreen(new Screen(birminghamSouth, 1, 80));
        birminghamSouth.addScreen(new Screen(birminghamSouth, 2, 100));
        cinemas.add(birminghamSouth);

        Cinema bristolCentral = new Cinema(
                "Horizon Bristol Central",
                "Bristol",
                "6 College Green, Bristol"
        );
        bristolCentral.addScreen(new Screen(bristolCentral, 1, 60));
        bristolCentral.addScreen(new Screen(bristolCentral, 2, 90));
        cinemas.add(bristolCentral);

        Cinema bristolHarbour = new Cinema(
                "Horizon Bristol Harbour",
                "Bristol",
                "19 Harbour Road, Bristol"
        );
        bristolHarbour.addScreen(new Screen(bristolHarbour, 1, 80));
        bristolHarbour.addScreen(new Screen(bristolHarbour, 2, 100));
        cinemas.add(bristolHarbour);

        Cinema cardiffBay = new Cinema(
                "Horizon Cardiff Bay",
                "Cardiff",
                "5 Bay Avenue, Cardiff"
        );
        cardiffBay.addScreen(new Screen(cardiffBay, 1, 70));
        cardiffBay.addScreen(new Screen(cardiffBay, 2, 90));
        cinemas.add(cardiffBay);

        Cinema cardiffNorth = new Cinema(
                "Horizon Cardiff North",
                "Cardiff",
                "31 North Road, Cardiff"
        );
        cardiffNorth.addScreen(new Screen(cardiffNorth, 1, 60));
        cardiffNorth.addScreen(new Screen(cardiffNorth, 2, 80));
        cinemas.add(cardiffNorth);

        cinemaRepository.saveAll(cinemas);

        System.out.println("HCBS demo cinemas created: " + cinemas.size());
    }

    private void seedFilmsIfEmpty(FilmRepository filmRepository) {
        if (filmRepository.count() > 0) {
            return;
        }

        List<Film> films = List.of(
                new Film(
                        "Project Hail Mary",
                        "Science teacher Ryland Grace wakes up on a spacecraft far from Earth with no memory of who he is or how he arrived there. As his memories return, he discovers a mission that could decide the future of humanity and force him to rely on science, courage and an unexpected friendship.",
                        "Ryan Gosling, Sandra Hüller, Lionel Boyce, Ken Leung, Milana Vayntrub",
                        "Phil Lord, Christopher Miller",
                        "Science Fiction",
                        "12A",
                        156,
                        LocalDate.of(2026, 3, 19),
                        "Moderate threat, rude humour, drug references, implied strong language",
                        "/images/posters/project-hail-mary.jpg"
                ),
                new Film(
                        "Michael",
                        "A cinematic portrait of Michael Jackson's life and legacy, following his journey from the Jackson Five to becoming one of the most influential entertainers in the world.",
                        "Jaafar Jackson, Nia Long, Laura Harrier, Juliano Krue Valdi, Miles Teller, Colman Domingo",
                        "Antoine Fuqua",
                        "Biography",
                        "12A",
                        127,
                        LocalDate.of(2026, 4, 22),
                        "Moderate threat, domestic abuse",
                        "/images/posters/michael.jpg"
                ),
                new Film(
                        "Star Wars: The Mandalorian and Grogu",
                        "The Mandalorian and Grogu begin a new adventure in a galaxy still recovering from the fall of the Empire. As scattered threats remain, the pair are drawn into another mission linked to the future of the New Republic.",
                        "Pedro Pascal, Sigourney Weaver, Jeremy Allen White",
                        "Jon Favreau",
                        "Fantasy",
                        "12A",
                        132,
                        LocalDate.of(2026, 5, 22),
                        "Moderate violence, threat, injury detail",
                        "/images/posters/star-wars-mandalorian-grogu.jpg"
                ),
                new Film(
                        "Chainsaw Man - The Movie: Reze Arc (Subbed)",
                        "Denji returns in a brutal new chapter where love, danger and survival collide. A mysterious girl named Reze enters his world, pulling him into a violent conflict between devils, hunters and hidden enemies.",
                        "Shiori Izawa, Reina Ueda, Kikunosuke Toya, Tomori Kusunoki, Shogo Sakata",
                        "Tatsuya Yoshihara",
                        "Animation",
                        "15",
                        100,
                        LocalDate.of(2026, 5, 26),
                        "Strong bloody violence, gore",
                        "/images/posters/chainsaw-man-reze-arc-subbed.jpg"
                ),
                new Film(
                        "GOAT",
                        "Will, a small goat with big dreams, gets the chance to join a team in the high-intensity sport of roarball. His new teammates doubt him at first, but Will is determined to prove that being small does not mean dreaming small.",
                        "Caleb McLaughlin, Gabrielle Union, Stephen Curry, Nicola Coughlan, Nick Kroll, David Harbour, Jennifer Hudson",
                        "Tyree Dillihay, Adam Rosette",
                        "Animation",
                        "PG",
                        100,
                        LocalDate.of(2026, 5, 15),
                        "Mild violence, rude humour, language",
                        "/images/posters/goat.jpg"
                ),
                new Film(
                        "The Devil Wears Prada 2",
                        "Two decades after their iconic turns in the world of fashion publishing, Miranda, Andy, Emily and Nigel return to the stylish offices and streets connected to Runway Magazine.",
                        "Meryl Streep, Anne Hathaway, Emily Blunt, Stanley Tucci",
                        "David Frankel",
                        "Comedy",
                        "12A",
                        119,
                        LocalDate.of(2026, 5, 1),
                        "Infrequent strong language",
                        "/images/posters/the-devil-wears-prada-2.jpg"
                ),
                new Film(
                        "Minions",
                        "Kevin, Stuart and Bob leave their isolated Minion tribe to search for a new villainous master. Their journey takes them to Scarlet Overkill, a stylish super-villain whose plan soon throws the Minions into comic chaos.",
                        "Sandra Bullock, Jon Hamm, Michael Keaton, Allison Janney, Steve Coogan, Jennifer Saunders, Pierre Coffin",
                        "Pierre Coffin, Kyle Balda",
                        "Animation",
                        "U",
                        91,
                        LocalDate.of(2015, 7, 10),
                        "Mild comic violence",
                        "/images/posters/minions.jpg"
                ),
                new Film(
                        "Zootopia 2",
                        "Rookie cops Judy Hopps and Nick Wilde return for a new case when Gary De'Snake arrives in Zootopia and turns the animal city upside down. To solve the mystery, Judy and Nick must go undercover in unfamiliar parts of the city, testing their partnership in new ways.",
                        "Ginnifer Goodwin, Jason Bateman, Ke Huy Quan, Fortune Feimster, Shakira, Idris Elba, Quinta Brunson, Andy Samberg",
                        "Jared Bush, Byron Howard",
                        "Animation",
                        "PG",
                        108,
                        LocalDate.of(2025, 11, 26),
                        "Rude humour, action violence",
                        "/images/posters/zootopia-2.jpg"
                )
        );

        filmRepository.saveAll(films);

        System.out.println("HCBS demo films created: " + films.size());
    }

    private void seedScreeningsIfEmpty(
            FilmRepository filmRepository,
            ScreenRepository screenRepository,
            ScreeningRepository screeningRepository
    ) {
        if (screeningRepository.count() > 0) {
            return;
        }

        List<Film> films = filmRepository.findAll();
        List<Screen> screens = screenRepository.findAll();

        if (films.isEmpty() || screens.isEmpty()) {
            System.out.println("Skipping demo screenings because films or screens are missing.");
            return;
        }

        films.sort(Comparator.comparing(Film::getTitle));
        screens.sort(Comparator.comparing(Screen::getId));

        Film chainsaw = findFilm(films, "Chainsaw Man - The Movie: Reze Arc (Subbed)");
        Film goat = findFilm(films, "GOAT");
        Film michael = findFilm(films, "Michael");
        Film minions = findFilm(films, "Minions");
        Film projectHailMary = findFilm(films, "Project Hail Mary");
        Film starWars = findFilm(films, "Star Wars: The Mandalorian and Grogu");
        Film devilWearsPrada = findFilm(films, "The Devil Wears Prada 2");
        Film zootopia = findFilm(films, "Zootopia 2");

        LocalDate today = LocalDate.now();

        List<Screening> screenings = new ArrayList<>();

        screenings.add(new Screening(projectHailMary, screenAt(screens, 0), today.plusDays(1), LocalTime.of(12, 0), ScreeningType.REGULAR));
        screenings.add(new Screening(projectHailMary, screenAt(screens, 0), today.plusDays(2), LocalTime.of(17, 50), ScreeningType.REGULAR));
        screenings.add(new Screening(projectHailMary, screenAt(screens, 5), today.plusDays(3), LocalTime.of(20, 40), ScreeningType.REGULAR));

        screenings.add(new Screening(michael, screenAt(screens, 1), today.plusDays(1), LocalTime.of(14, 50), ScreeningType.REGULAR));
        screenings.add(new Screening(michael, screenAt(screens, 10), today.plusDays(2), LocalTime.of(18, 0), ScreeningType.REGULAR));
        screenings.add(new Screening(michael, screenAt(screens, 14), today.plusDays(4), LocalTime.of(20, 45), ScreeningType.REGULAR));

        screenings.add(new Screening(starWars, screenAt(screens, 6), today.plusDays(1), LocalTime.of(15, 15), ScreeningType.ADVANCE_PREVIEW));
        screenings.add(new Screening(starWars, screenAt(screens, 2), today.plusDays(3), LocalTime.of(18, 0), ScreeningType.ADVANCE_PREVIEW));
        screenings.add(new Screening(starWars, screenAt(screens, 12), today.plusDays(5), LocalTime.of(20, 45), ScreeningType.ADVANCE_PREVIEW));

        screenings.add(new Screening(chainsaw, screenAt(screens, 15), today.plusDays(2), LocalTime.of(17, 30), ScreeningType.ADVANCE_PREVIEW));
        screenings.add(new Screening(chainsaw, screenAt(screens, 1), today.plusDays(4), LocalTime.of(22, 0), ScreeningType.ADVANCE_PREVIEW));

        screenings.add(new Screening(goat, screenAt(screens, 10), today.plusDays(1), LocalTime.of(10, 30), ScreeningType.ADVANCE_PREVIEW));
        screenings.add(new Screening(goat, screenAt(screens, 14), today.plusDays(3), LocalTime.of(13, 20), ScreeningType.REGULAR));
        screenings.add(new Screening(goat, screenAt(screens, 5), today.plusDays(6), LocalTime.of(16, 0), ScreeningType.REGULAR));

        screenings.add(new Screening(devilWearsPrada, screenAt(screens, 1), today.plusDays(1), LocalTime.of(15, 30), ScreeningType.REGULAR));
        screenings.add(new Screening(devilWearsPrada, screenAt(screens, 6), today.plusDays(2), LocalTime.of(18, 30), ScreeningType.REGULAR));
        screenings.add(new Screening(devilWearsPrada, screenAt(screens, 12), today.plusDays(5), LocalTime.of(21, 0), ScreeningType.REGULAR));

        screenings.add(new Screening(minions, screenAt(screens, 0), today.plusDays(1), LocalTime.of(10, 20), ScreeningType.REGULAR));
        screenings.add(new Screening(minions, screenAt(screens, 5), today.plusDays(2), LocalTime.of(13, 40), ScreeningType.REGULAR));
        screenings.add(new Screening(minions, screenAt(screens, 10), today.plusDays(4), LocalTime.of(16, 10), ScreeningType.REGULAR));

        screenings.add(new Screening(zootopia, screenAt(screens, 1), today.plusDays(1), LocalTime.of(11, 30), ScreeningType.REGULAR));
        screenings.add(new Screening(zootopia, screenAt(screens, 14), today.plusDays(3), LocalTime.of(15, 20), ScreeningType.REGULAR));
        screenings.add(new Screening(zootopia, screenAt(screens, 6), today.plusDays(5), LocalTime.of(18, 30), ScreeningType.REGULAR));
        screenings.add(new Screening(zootopia, screenAt(screens, 3), today.plusDays(6), LocalTime.of(20, 30), ScreeningType.REGULAR));

        screeningRepository.saveAll(screenings);

        System.out.println("HCBS demo screenings created: " + screenings.size());
    }

    private Film findFilm(List<Film> films, String title) {
        return films.stream()
                .filter(film -> film.getTitle().equalsIgnoreCase(title))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Demo film not found: " + title));
    }

    private Screen screenAt(List<Screen> screens, int index) {
        if (screens.isEmpty()) {
            throw new IllegalStateException("No screens available for demo screenings.");
        }

        int safeIndex = Math.floorMod(index, screens.size());
        return screens.get(safeIndex);
    }

    private void ensureSeatsExistForAllScreens(
            ScreenRepository screenRepository,
            SeatRepository seatRepository
    ) {
        List<Screen> screens = screenRepository.findAll();

        for (Screen screen : screens) {
            List<Seat> existingSeats = seatRepository.findByScreenIdOrderBySeatNumberAsc(screen.getId());

            Set<String> existingSeatNumbers = new HashSet<>();
            for (Seat seat : existingSeats) {
                existingSeatNumbers.add(seat.getSeatNumber());
            }

            List<Seat> missingSeats = generateMissingSeats(screen, existingSeatNumbers);

            if (!missingSeats.isEmpty()) {
                seatRepository.saveAll(missingSeats);
                System.out.println(
                        "Generated " + missingSeats.size()
                                + " missing seats for Screen "
                                + screen.getScreenNumber()
                                + " at "
                                + screen.getCinema().getName()
                );
            }
        }
    }

    private List<Seat> generateMissingSeats(Screen screen, Set<String> existingSeatNumbers) {
        List<Seat> seats = new ArrayList<>();
        int capacity = screen.getCapacity();

        for (int i = 0; i < capacity; i++) {
            String label = buildSeatLabel(i);

            if (existingSeatNumbers.contains(label)) {
                continue;
            }

            SeatType seatType = i < capacity * 0.7
                    ? SeatType.LOWER_HALL
                    : SeatType.UPPER_GALLERY;

            seats.add(new Seat(screen, label, seatType));
        }

        return seats;
    }

    private String buildSeatLabel(int index) {
        char row = (char) ('A' + (index / SEATS_PER_ROW));
        int seatNumber = (index % SEATS_PER_ROW) + 1;

        return row + String.valueOf(seatNumber);
    }
}