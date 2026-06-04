package com.eduaccess.service;

import com.eduaccess.domain.*;
import com.eduaccess.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_020 / IT_021 — Integration tests for {@link AuditLogService}.
 * Verifies that audit log entries are correctly persisted in the
 * {@code operation_audit_logs} table via the full Spring context.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:audit-it;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "vaadin.launch-browser=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AuditLogServiceIT {

    @Autowired private AuditLogService auditLogService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private EntityManager entityManager;

    private Booking booking;

    @BeforeEach
    void setUp() {
        Cinema cinema = new Cinema("Audit Cinema", "London", "1 Audit Rd");
        entityManager.persist(cinema);
        Screen screen = new Screen(cinema, 1, 50, HallType.REGULAR);
        entityManager.persist(screen);
        Film film = new Film("Audit Film", "Desc", "Actor", "Drama", "12A", 100);
        entityManager.persist(film);
        Screening screening = new Screening(film, screen, LocalDate.now().plusDays(1), LocalTime.of(19, 0));
        entityManager.persist(screening);
        booking = new Booking("HCBS-AIT-001", screening, "Alice", "alice@test.com");
        booking.setTotalCost(new BigDecimal("20.00"));
        entityManager.persist(booking);
        entityManager.flush();
    }

    @Test
    @DisplayName("bookingAction_persistsAuditEntry")
    void bookingAction_persistsAuditEntry() {
        long countBefore = auditLogRepository.count();

        auditLogService.record(
                AuditAction.BOOKING_CREATED,
                "Booking",
                booking.getId(),
                booking.getBookingReference(),
                "Audit Film",
                "Audit Cinema",
                booking.getTotalCost(),
                "Booking created for Audit Film",
                "Seats: A1, A2"
        );

        entityManager.flush();

        long countAfter = auditLogRepository.count();
        assertThat(countAfter).isEqualTo(countBefore + 1);

        AuditLog latest = auditLogRepository.findTop200ByOrderByCreatedAtDesc().get(0);
        assertThat(latest.getAction()).isEqualTo(AuditAction.BOOKING_CREATED);
        assertThat(latest.getEntityType()).isEqualTo("Booking");
        assertThat(latest.getReference()).isEqualTo("HCBS-AIT-001");
    }

    @Test
    @DisplayName("IT_021 scheduleAction_persistsActorAndEntity")
    void scheduleAction_persistsActorAndEntity() {
        auditLogService.record(
                AuditAction.SCREENING_CREATED,
                "Screening",
                99L,
                null,
                "Audit Film",
                "Audit Cinema",
                null,
                "Screening created",
                "Screen 1; 19:00"
        );

        entityManager.flush();

        AuditLog latest = auditLogRepository.findTop200ByOrderByCreatedAtDesc().get(0);
        assertThat(latest.getAction()).isEqualTo(AuditAction.SCREENING_CREATED);
        assertThat(latest.getActorUsername()).isNotNull();
        assertThat(latest.getEntityType()).isEqualTo("Screening");
        assertThat(latest.getEntityId()).isEqualTo(99L);
        assertThat(latest.getCreatedAt()).isNotNull();
    }
}
