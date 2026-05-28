package com.eduaccess.config;

import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.Film;
import com.eduaccess.domain.FoodCategory;
import com.eduaccess.domain.FoodItem;
import com.eduaccess.domain.HallType;
import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.domain.Seat;
import com.eduaccess.domain.SeatType;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.repository.FoodItemRepository;
import com.eduaccess.repository.ScreenRepository;
import com.eduaccess.repository.ScreeningRepository;
import com.eduaccess.repository.SeatRepository;
import com.eduaccess.domain.UserAccount;
import com.eduaccess.domain.UserRole;
import com.eduaccess.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class DataInitializer {

    private static final int REGULAR_SEATS_PER_ROW = 10;
    private static final int IMAX_SEATS_PER_ROW = 8;
    private static final int PREMIUM_SEATS_PER_ROW = 6;

    @Bean
    CommandLineRunner maintainSeedData(
            CinemaRepository cinemaRepository,
            FilmRepository filmRepository,
            ScreenRepository screenRepository,
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository,
            UserAccountRepository userAccountRepository,
            FoodItemRepository foodItemRepository,
            EntityManager entityManager,
            DataSource dataSource,
            PlatformTransactionManager transactionManager
    ) {
        return args -> {
            // ── Self-heal: drop any legacy CHECK constraint that mentions the
            // STATUS column on the bookings table BEFORE the JPA transaction
            // starts. Native DDL inside a JPA-managed transaction would mark
            // it rollback-only on failure, so we run this on its own JDBC
            // connection in autocommit mode.
            purgeBookingStatusCheckConstraints(dataSource);

            // ── Self-heal: also drop legacy CHECK constraints on the audit
            // log table. New AuditAction enum values (FILM_CREATED / UPDATED /
            // DELETED, etc.) would otherwise be rejected by stale CHECK
            // clauses generated against earlier enum value sets.
            purgeAuditLogCheckConstraints(dataSource);

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

                seedUsersIfEmpty(userAccountRepository);
                seedFoodItemsIfEmpty(foodItemRepository);

                System.out.println("HCBS data maintenance completed.");
                System.out.println("Expired unbooked screenings removed: " + deletedScreenings);
                System.out.println("Films in database: " + filmRepository.count());
                System.out.println("Cinemas in database: " + cinemaRepository.count());
                System.out.println("Screens in database: " + screenRepository.count());
                System.out.println("Screenings in database: " + screeningRepository.count());
                System.out.println("User accounts in database: " + userAccountRepository.count());
                System.out.println("Food items in database: " + foodItemRepository.count());
            });
        };
    }

    /**
     * Drops every CHECK constraint currently attached to the
     * {@code operation_audit_logs} table. Idempotent.
     * <p>
     * Required because Hibernate generates CHECK (action IN (...)) clauses
     * from the current {@link com.eduaccess.domain.AuditAction} enum, and
     * {@code ddl-auto=update} does not refresh those clauses when new enum
     * values are added. Removing the constraints lets new actions persist;
     * data integrity for the enum is still enforced at the Java layer.
     */
    private void purgeAuditLogCheckConstraints(DataSource dataSource) {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            String sql = "SELECT tc.CONSTRAINT_NAME "
                    + "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                    + "WHERE UPPER(tc.TABLE_NAME) = 'OPERATION_AUDIT_LOGS' "
                    + "  AND tc.CONSTRAINT_TYPE = 'CHECK'";
            try (java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
                for (String name : names) {
                    System.out.println("[DataInitializer] Dropping legacy CHECK on operation_audit_logs: "
                            + name);
                    try (java.sql.Statement drop = conn.createStatement()) {
                        drop.executeUpdate(
                                "ALTER TABLE OPERATION_AUDIT_LOGS DROP CONSTRAINT IF EXISTS " + name);
                    }
                }
            }
        } catch (java.sql.SQLException ex) {
            System.out.println("[DataInitializer] Audit-log CHECK-constraint scan failed: "
                    + ex.getMessage());
        }
    }

    /**
     * Drops every CHECK constraint currently attached to the {@code bookings}
     * table that mentions the {@code status} column. Idempotent.
     * <p>
     * Runs on a fresh JDBC connection (autocommit) so that any failure does
     * NOT mark the surrounding JPA transaction as rollback-only.
     */
    private void purgeBookingStatusCheckConstraints(DataSource dataSource) {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            // H2 2.x : CHECK constraints live in INFORMATION_SCHEMA.CHECK_CONSTRAINTS
            // joined to TABLE_CONSTRAINTS for table filtering.
            String sql = "SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE "
                    + "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                    + "JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc "
                    + "  ON tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME "
                    + "WHERE UPPER(tc.TABLE_NAME) = 'BOOKINGS' "
                    + "  AND tc.CONSTRAINT_TYPE = 'CHECK'";
            try (java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                List<String[]> hits = new ArrayList<>();
                while (rs.next()) {
                    String name = rs.getString(1);
                    String clause = rs.getString(2);
                    hits.add(new String[]{name, clause});
                }
                for (String[] hit : hits) {
                    String name = hit[0];
                    String clause = hit[1] == null ? "" : hit[1].toUpperCase();
                    if (clause.contains("STATUS")) {
                        System.out.println("[DataInitializer] Dropping legacy CHECK on bookings: "
                                + name + " => " + hit[1]);
                        try (java.sql.Statement drop = conn.createStatement()) {
                            drop.executeUpdate(
                                    "ALTER TABLE BOOKINGS DROP CONSTRAINT IF EXISTS " + name);
                        }
                    }
                }
            }
        } catch (java.sql.SQLException ex) {
            System.out.println("[DataInitializer] CHECK-constraint scan failed: "
                    + ex.getMessage());
        }
    }

    /**
     * Legacy EntityManager-based version kept for reference; no longer
     * invoked because native DDL inside a JPA transaction marks it
     * rollback-only on failure. The {@link #purgeBookingStatusCheckConstraints(DataSource)}
     * variant runs outside any JPA transaction instead.
     */
    @SuppressWarnings({"unchecked", "unused"})
    private void purgeBookingStatusCheckConstraints(EntityManager entityManager) {
        try {
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT CONSTRAINT_NAME, SQL FROM INFORMATION_SCHEMA.CONSTRAINTS "
                            + "WHERE TABLE_NAME = 'BOOKINGS' AND CONSTRAINT_TYPE = 'CHECK'")
                    .getResultList();
            for (Object[] row : rows) {
                String name = String.valueOf(row[0]);
                String sql = row[1] == null ? "" : String.valueOf(row[1]).toUpperCase();
                if (sql.contains("STATUS")) {
                    System.out.println("[DataInitializer] Dropping legacy CHECK on bookings: "
                            + name + " => " + row[1]);
                    entityManager.createNativeQuery(
                                    "ALTER TABLE BOOKINGS DROP CONSTRAINT IF EXISTS " + name)
                            .executeUpdate();
                }
            }
        } catch (RuntimeException ex) {
            System.out.println("[DataInitializer] CHECK-constraint scan failed ("
                    + ex.getMessage() + "), falling back to best-effort drop.");
            for (char c = 'A'; c <= 'Z'; c++) {
                String name = "CONSTRAINT_" + c;
                try {
                    entityManager.createNativeQuery(
                                    "ALTER TABLE BOOKINGS DROP CONSTRAINT IF EXISTS " + name)
                            .executeUpdate();
                } catch (RuntimeException ignored) {
                    // best effort
                }
            }
        }
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
        londonCentral.addScreen(new Screen(londonCentral, 1, 80, HallType.IMAX));
        londonCentral.addScreen(new Screen(londonCentral, 2, 60, HallType.REGULAR));
        londonCentral.addScreen(new Screen(londonCentral, 3, 48, HallType.PREMIUM));
        cinemas.add(londonCentral);

        Cinema londonRiverside = new Cinema(
                "Horizon London Riverside",
                "London",
                "45 River Road, London"
        );
        londonRiverside.addScreen(new Screen(londonRiverside, 1, 80, HallType.IMAX));
        londonRiverside.addScreen(new Screen(londonRiverside, 2, 70, HallType.REGULAR));
        cinemas.add(londonRiverside);

        Cinema birminghamCity = new Cinema(
                "Horizon Birmingham City",
                "Birmingham",
                "8 New Street, Birmingham"
        );
        birminghamCity.addScreen(new Screen(birminghamCity, 1, 70, HallType.REGULAR));
        birminghamCity.addScreen(new Screen(birminghamCity, 2, 80, HallType.IMAX));
        birminghamCity.addScreen(new Screen(birminghamCity, 3, 100, HallType.REGULAR));
        cinemas.add(birminghamCity);

        Cinema birminghamSouth = new Cinema(
                "Horizon Birmingham South",
                "Birmingham",
                "22 Park Lane, Birmingham"
        );
        birminghamSouth.addScreen(new Screen(birminghamSouth, 1, 80, HallType.REGULAR));
        birminghamSouth.addScreen(new Screen(birminghamSouth, 2, 48, HallType.PREMIUM));
        cinemas.add(birminghamSouth);

        Cinema bristolCentral = new Cinema(
                "Horizon Bristol Central",
                "Bristol",
                "6 College Green, Bristol"
        );
        bristolCentral.addScreen(new Screen(bristolCentral, 1, 60, HallType.REGULAR));
        bristolCentral.addScreen(new Screen(bristolCentral, 2, 80, HallType.IMAX));
        cinemas.add(bristolCentral);

        Cinema bristolHarbour = new Cinema(
                "Horizon Bristol Harbour",
                "Bristol",
                "19 Harbour Road, Bristol"
        );
        bristolHarbour.addScreen(new Screen(bristolHarbour, 1, 48, HallType.PREMIUM));
        bristolHarbour.addScreen(new Screen(bristolHarbour, 2, 100, HallType.REGULAR));
        cinemas.add(bristolHarbour);

        Cinema cardiffBay = new Cinema(
                "Horizon Cardiff Bay",
                "Cardiff",
                "5 Bay Avenue, Cardiff"
        );
        cardiffBay.addScreen(new Screen(cardiffBay, 1, 70, HallType.REGULAR));
        cardiffBay.addScreen(new Screen(cardiffBay, 2, 80, HallType.IMAX));
        cinemas.add(cardiffBay);

        Cinema cardiffNorth = new Cinema(
                "Horizon Cardiff North",
                "Cardiff",
                "31 North Road, Cardiff"
        );
        cardiffNorth.addScreen(new Screen(cardiffNorth, 1, 60, HallType.REGULAR));
        cardiffNorth.addScreen(new Screen(cardiffNorth, 2, 48, HallType.PREMIUM));
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

        // Project Hail Mary - mixed 2D and 3D
        screenings.add(new Screening(projectHailMary, screenAt(screens, 0), today.plusDays(1), LocalTime.of(10, 0), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(projectHailMary, screenAt(screens, 0), today.plusDays(1), LocalTime.of(14, 0), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(projectHailMary, screenAt(screens, 0), today.plusDays(1), LocalTime.of(17, 50), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(projectHailMary, screenAt(screens, 6), today.plusDays(2), LocalTime.of(15, 0), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(projectHailMary, screenAt(screens, 5), today.plusDays(2), LocalTime.of(20, 40), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(projectHailMary, screenAt(screens, 11), today.plusDays(3), LocalTime.of(18, 0), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(projectHailMary, screenAt(screens, 15), today.plusDays(4), LocalTime.of(19, 30), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(projectHailMary, screenAt(screens, 2), today.plusDays(5), LocalTime.of(21, 0), ScreeningType.REGULAR_3D));

        // Michael - mixed 2D and 3D
        screenings.add(new Screening(michael, screenAt(screens, 1), today.plusDays(1), LocalTime.of(11, 0), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(michael, screenAt(screens, 1), today.plusDays(1), LocalTime.of(14, 50), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(michael, screenAt(screens, 10), today.plusDays(2), LocalTime.of(18, 0), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(michael, screenAt(screens, 14), today.plusDays(3), LocalTime.of(16, 30), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(michael, screenAt(screens, 14), today.plusDays(4), LocalTime.of(20, 45), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(michael, screenAt(screens, 7), today.plusDays(5), LocalTime.of(19, 0), ScreeningType.REGULAR_3D));

        // Star Wars - advance preview with 2D/3D
        screenings.add(new Screening(starWars, screenAt(screens, 6), today.plusDays(1), LocalTime.of(15, 15), ScreeningType.ADVANCE_PREVIEW_2D));
        screenings.add(new Screening(starWars, screenAt(screens, 6), today.plusDays(1), LocalTime.of(19, 0), ScreeningType.ADVANCE_PREVIEW_3D));
        screenings.add(new Screening(starWars, screenAt(screens, 2), today.plusDays(3), LocalTime.of(18, 0), ScreeningType.ADVANCE_PREVIEW_3D));
        screenings.add(new Screening(starWars, screenAt(screens, 12), today.plusDays(4), LocalTime.of(17, 30), ScreeningType.ADVANCE_PREVIEW_2D));
        screenings.add(new Screening(starWars, screenAt(screens, 12), today.plusDays(5), LocalTime.of(20, 45), ScreeningType.ADVANCE_PREVIEW_3D));
        screenings.add(new Screening(starWars, screenAt(screens, 0), today.plusDays(6), LocalTime.of(19, 30), ScreeningType.ADVANCE_PREVIEW_2D));

        // Chainsaw Man - advance preview
        screenings.add(new Screening(chainsaw, screenAt(screens, 15), today.plusDays(2), LocalTime.of(17, 30), ScreeningType.ADVANCE_PREVIEW_2D));
        screenings.add(new Screening(chainsaw, screenAt(screens, 15), today.plusDays(2), LocalTime.of(20, 30), ScreeningType.ADVANCE_PREVIEW_3D));
        screenings.add(new Screening(chainsaw, screenAt(screens, 1), today.plusDays(4), LocalTime.of(22, 0), ScreeningType.ADVANCE_PREVIEW_2D));
        screenings.add(new Screening(chainsaw, screenAt(screens, 6), today.plusDays(5), LocalTime.of(21, 0), ScreeningType.ADVANCE_PREVIEW_3D));

        // GOAT - mix of advance preview and regular
        screenings.add(new Screening(goat, screenAt(screens, 10), today.plusDays(1), LocalTime.of(10, 30), ScreeningType.ADVANCE_PREVIEW_2D));
        screenings.add(new Screening(goat, screenAt(screens, 14), today.plusDays(2), LocalTime.of(14, 0), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(goat, screenAt(screens, 14), today.plusDays(3), LocalTime.of(13, 20), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(goat, screenAt(screens, 5), today.plusDays(4), LocalTime.of(16, 0), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(goat, screenAt(screens, 10), today.plusDays(5), LocalTime.of(11, 0), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(goat, screenAt(screens, 2), today.plusDays(6), LocalTime.of(15, 30), ScreeningType.REGULAR_2D));

        // The Devil Wears Prada 2
        screenings.add(new Screening(devilWearsPrada, screenAt(screens, 1), today.plusDays(1), LocalTime.of(15, 30), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(devilWearsPrada, screenAt(screens, 6), today.plusDays(2), LocalTime.of(18, 30), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(devilWearsPrada, screenAt(screens, 12), today.plusDays(3), LocalTime.of(17, 0), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(devilWearsPrada, screenAt(screens, 12), today.plusDays(5), LocalTime.of(21, 0), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(devilWearsPrada, screenAt(screens, 1), today.plusDays(6), LocalTime.of(19, 30), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(devilWearsPrada, screenAt(screens, 7), today.plusDays(7), LocalTime.of(20, 0), ScreeningType.REGULAR_3D));

        // Minions
        screenings.add(new Screening(minions, screenAt(screens, 0), today.plusDays(1), LocalTime.of(10, 20), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(minions, screenAt(screens, 5), today.plusDays(2), LocalTime.of(13, 40), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(minions, screenAt(screens, 10), today.plusDays(3), LocalTime.of(11, 0), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(minions, screenAt(screens, 10), today.plusDays(4), LocalTime.of(16, 10), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(minions, screenAt(screens, 2), today.plusDays(5), LocalTime.of(14, 30), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(minions, screenAt(screens, 0), today.plusDays(6), LocalTime.of(10, 0), ScreeningType.REGULAR_3D));

        // Zootopia 2
        screenings.add(new Screening(zootopia, screenAt(screens, 1), today.plusDays(1), LocalTime.of(11, 30), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(zootopia, screenAt(screens, 14), today.plusDays(2), LocalTime.of(15, 20), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(zootopia, screenAt(screens, 6), today.plusDays(3), LocalTime.of(18, 30), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(zootopia, screenAt(screens, 3), today.plusDays(4), LocalTime.of(20, 30), ScreeningType.REGULAR_3D));
        screenings.add(new Screening(zootopia, screenAt(screens, 12), today.plusDays(5), LocalTime.of(16, 0), ScreeningType.REGULAR_2D));
        screenings.add(new Screening(zootopia, screenAt(screens, 2), today.plusDays(6), LocalTime.of(19, 0), ScreeningType.REGULAR_3D));

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
        HallType hallType = screen.getHallType();
        int seatsPerRow = getSeatsPerRow(hallType);

        for (int i = 0; i < capacity; i++) {
            String label = buildSeatLabel(i, seatsPerRow);

            if (existingSeatNumbers.contains(label)) {
                continue;
            }

            SeatType seatType = getSeatTypeForPosition(i, capacity, hallType);

            seats.add(new Seat(screen, label, seatType));
        }

        return seats;
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

    private void seedFoodItemsIfEmpty(FoodItemRepository foodItemRepository) {
        if (foodItemRepository.count() > 0) {
            return;
        }

        List<FoodItem> items = List.of(
                new FoodItem("Small Popcorn", FoodCategory.POPCORN, new BigDecimal("3.50"), null, true),
                new FoodItem("Large Popcorn", FoodCategory.POPCORN, new BigDecimal("5.50"), null, true),
                new FoodItem("French Fries", FoodCategory.FRIES, new BigDecimal("3.25"), null, true),
                new FoodItem("Cheese Fries", FoodCategory.FRIES, new BigDecimal("4.25"), null, true),
                new FoodItem("Coca-Cola", FoodCategory.DRINK, new BigDecimal("2.80"), null, true),
                new FoodItem("Sprite", FoodCategory.DRINK, new BigDecimal("2.80"), null, true),
                new FoodItem("Still Water", FoodCategory.DRINK, new BigDecimal("2.20"), null, true),
                new FoodItem("Movie Combo", FoodCategory.COMBO, new BigDecimal("8.50"), null, true),
                new FoodItem("Family Snack Box", FoodCategory.COMBO, new BigDecimal("15.00"), null, true)
        );

        foodItemRepository.saveAll(items);
        System.out.println("HCBS demo food items created: " + items.size());
    }

    private void seedUsersIfEmpty(UserAccountRepository userAccountRepository) {
        // Idempotent per-username seeding: ensures the three demo role accounts
        // (manager / admin / staff) always exist even if a teammate's DB was
        // previously populated with their own user records. Without this,
        // teammates who pulled the project couldn't see role-gated views
        // (e.g. Manager Cinemas / Films Mgmt) because the seeds only ran when
        // the user table was completely empty.
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        int created = 0;

        if (!userAccountRepository.existsByUsername("manager")) {
            userAccountRepository.save(new UserAccount(
                    "manager", encoder.encode("manager123"),
                    "manager@hcbs.com", "Cinema Manager", UserRole.MANAGER));
            created++;
        }

        if (!userAccountRepository.existsByUsername("admin")) {
            userAccountRepository.save(new UserAccount(
                    "admin", encoder.encode("admin123"),
                    "admin@hcbs.com", "System Admin", UserRole.ADMIN));
            created++;
        }

        if (!userAccountRepository.existsByUsername("staff")) {
            userAccountRepository.save(new UserAccount(
                    "staff", encoder.encode("staff123"),
                    "staff@hcbs.com", "Booking Staff", UserRole.BOOKING_STAFF));
            created++;
        }

        if (created > 0) {
            System.out.println("HCBS demo user accounts ensured (newly created: " + created + ")");
        }
    }
}