package com.eduaccess.config;

import com.eduaccess.domain.UserAccount;
import com.eduaccess.domain.UserRole;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.repository.FoodItemRepository;
import com.eduaccess.repository.ScreenRepository;
import com.eduaccess.repository.ScreeningRepository;
import com.eduaccess.repository.SeatRepository;
import com.eduaccess.repository.UserAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_014 — Integration test for {@link DataInitializer}.
 * <p>
 * Boots the full Spring context, which causes the {@code maintainSeedData}
 * {@link CommandLineRunner} to run once. The test then re-invokes the same
 * runner and verifies that NO duplicate rows are inserted (idempotency
 * guard for the “seed-if-empty / per-username” logic).
 * <p>
 * This protects against a class of regressions where a teammate adds a
 * new {@code save(...)} call without an existence check, which would
 * silently double-insert seed data on every restart and corrupt the
 * Manager Dashboard analytics.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DataInitializerIT {

    @Autowired
    @Qualifier("maintainSeedData")
    private CommandLineRunner maintainSeedData;

    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private FilmRepository filmRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private ScreeningRepository screeningRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private FoodItemRepository foodItemRepository;

    @Test
    @DisplayName("seedData_doesNotDuplicateOnRestart")
    void seedData_doesNotDuplicateOnRestart() throws Exception {
        // ── Baseline: the @SpringBootTest startup already invoked the
        // CommandLineRunner once, so seed data should be present.
        long cinemasBefore    = cinemaRepository.count();
        long filmsBefore      = filmRepository.count();
        long screensBefore    = screenRepository.count();
        long screeningsBefore = screeningRepository.count();
        long seatsBefore      = seatRepository.count();
        long usersBefore      = userAccountRepository.count();
        long foodItemsBefore  = foodItemRepository.count();

        assertThat(cinemasBefore).as("Cinemas must be seeded on startup").isGreaterThan(0);
        assertThat(filmsBefore).as("Films must be seeded on startup").isGreaterThan(0);
        assertThat(screensBefore).as("Screens must be seeded on startup").isGreaterThan(0);
        assertThat(usersBefore).as("Users must be seeded on startup").isGreaterThan(0);
        assertThat(foodItemsBefore).as("Food items must be seeded on startup").isGreaterThan(0);

        // ── The three demo role accounts must always exist (per-username seeding).
        assertThat(userAccountRepository.existsByUsername("manager")).isTrue();
        assertThat(userAccountRepository.existsByUsername("admin")).isTrue();
        assertThat(userAccountRepository.existsByUsername("staff")).isTrue();

        Optional<UserAccount> manager = userAccountRepository.findByUsername("manager");
        assertThat(manager).isPresent();
        assertThat(manager.get().getRole()).isEqualTo(UserRole.MANAGER);

        // ── Re-invoke the seed runner — simulates an application restart
        // hitting an already-populated database. Nothing must duplicate.
        maintainSeedData.run();

        assertThat(cinemaRepository.count())
                .as("Cinemas count must NOT change on re-run").isEqualTo(cinemasBefore);
        assertThat(filmRepository.count())
                .as("Films count must NOT change on re-run").isEqualTo(filmsBefore);
        assertThat(screenRepository.count())
                .as("Screens count must NOT change on re-run").isEqualTo(screensBefore);
        assertThat(screeningRepository.count())
                .as("Screenings count must NOT change on re-run").isEqualTo(screeningsBefore);
        assertThat(seatRepository.count())
                .as("Seats count must NOT change on re-run").isEqualTo(seatsBefore);
        assertThat(userAccountRepository.count())
                .as("User count must NOT change on re-run (per-username guard)").isEqualTo(usersBefore);
        assertThat(foodItemRepository.count())
                .as("Food items count must NOT change on re-run").isEqualTo(foodItemsBefore);
    }
}
