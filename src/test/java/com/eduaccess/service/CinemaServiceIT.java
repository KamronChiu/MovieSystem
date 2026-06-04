package com.eduaccess.service;

import com.eduaccess.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_026 — Integration test for {@link CinemaService#createCinema}.
 * Verifies that creating a cinema also persists the correct number
 * of screens and auto-generated seats.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cinema-it;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "vaadin.launch-browser=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CinemaServiceIT {

    @Autowired private CinemaService cinemaService;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("IT_026 createCinema_createsScreensAndSeats")
    void createCinema_createsScreensAndSeats() {
        // Create a cinema with 2 screens (50 and 80 capacity)
        Cinema cinema = cinemaService.createCinema(
                "Birmingham",
                "HCBS IT Cinema",
                "123 Test Lane",
                List.of(50, 80)
        );

        assertThat(cinema).isNotNull();
        assertThat(cinema.getId()).isNotNull();
        assertThat(cinema.getName()).isEqualTo("HCBS IT Cinema");
        assertThat(cinema.getCity()).isEqualTo("Birmingham");

        // Verify screens created
        List<Screen> screens = cinemaService.findScreensForCinema(cinema.getId());
        assertThat(screens).hasSize(2);
        assertThat(screens.get(0).getCapacity()).isEqualTo(50);
        assertThat(screens.get(1).getCapacity()).isEqualTo(80);

        // Verify seats auto-generated for each screen
        assertThat(screens.get(0).getSeats()).hasSize(50);
        assertThat(screens.get(1).getSeats()).hasSize(80);
    }
}
