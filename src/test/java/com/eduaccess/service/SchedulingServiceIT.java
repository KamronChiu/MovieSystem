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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT_025 — Integration test for {@link SchedulingService}.
 * Verifies that overlapping screenings are rejected before persistence.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scheduling-it;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "vaadin.launch-browser=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class SchedulingServiceIT {

    @Autowired private SchedulingService schedulingService;
    @Autowired private EntityManager entityManager;

    private Film film;
    private Screen screen;

    @BeforeEach
    void setUp() {
        Cinema cinema = new Cinema("Schedule Cinema", "London", "1 Schedule Rd");
        entityManager.persist(cinema);
        screen = new Screen(cinema, 1, 80, HallType.REGULAR);
        entityManager.persist(screen);
        film = new Film("Schedule Film", "Desc", "Actor", "Action", "12A", 120);
        entityManager.persist(film);
        entityManager.flush();

        // Create an existing screening: tomorrow 14:00-16:00
        schedulingService.createScreening(
                film.getId(), screen.getId(),
                LocalDate.now().plusDays(1), LocalTime.of(14, 0)
        );
    }

    @Test
    @DisplayName("createScreening_overlapRejected")
    void createScreening_overlapRejected() {
        // Attempt to create a screening overlapping with existing (14:00-16:00)
        // New screening: 15:00-17:00 should overlap
        assertThatThrownBy(() -> schedulingService.createScreening(
                film.getId(), screen.getId(),
                LocalDate.now().plusDays(1), LocalTime.of(15, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overlaps");
    }
}
