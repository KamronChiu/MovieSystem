package com.eduaccess.service.batch;

import com.eduaccess.domain.*;
import com.eduaccess.repository.AuditLogRepository;
import com.eduaccess.repository.CancellationRepository;
import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundScope;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_027 — Integration test for {@link BatchCancellationService#executeBatch}.
 * Verifies that executing an emergency batch refund creates
 * CancellationRecords and audit log entries for each successfully
 * processed booking.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:batch-it;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "vaadin.launch-browser=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class BatchCancellationServiceIT {

    @Autowired private BatchCancellationService batchCancellationService;
    @Autowired private CancellationRepository cancellationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private EntityManager entityManager;

    private Booking booking1;
    private Booking booking2;

    @BeforeEach
    void setUp() {
        Cinema cinema = new Cinema("Batch Cinema", "London", "1 Batch Rd");
        entityManager.persist(cinema);
        Screen screen = new Screen(cinema, 1, 60, HallType.REGULAR);
        entityManager.persist(screen);
        Film film = new Film("Batch Film", "Desc", "Actor", "Action", "12A", 100);
        entityManager.persist(film);

        Screening screening = new Screening(film, screen,
                LocalDate.now().plusDays(1), LocalTime.of(20, 0));
        entityManager.persist(screening);

        Seat seatA1 = new Seat(screen, "A1", SeatType.STANDARD);
        Seat seatA2 = new Seat(screen, "A2", SeatType.STANDARD);
        entityManager.persist(seatA1);
        entityManager.persist(seatA2);

        booking1 = new Booking("HCBS-BATCH-001", screening, "Alice", "alice@test.com");
        booking1.setTotalCost(new BigDecimal("25.00"));
        booking1.addBookingSeat(new BookingSeat(booking1, seatA1, new BigDecimal("25.00")));
        entityManager.persist(booking1);

        booking2 = new Booking("HCBS-BATCH-002", screening, "Bob", "bob@test.com");
        booking2.setTotalCost(new BigDecimal("30.00"));
        booking2.addBookingSeat(new BookingSeat(booking2, seatA2, new BigDecimal("30.00")));
        entityManager.persist(booking2);

        entityManager.flush();
        entityManager.clear();

        // Re-load to get managed entities
        booking1 = entityManager.find(Booking.class, booking1.getId());
        booking2 = entityManager.find(Booking.class, booking2.getId());
    }

    @Test
    @DisplayName("executeBatch_createsRecordsAndEmails")
    void executeBatch_createsRecordsAndEmails() {
        long auditCountBefore = auditLogRepository.count();
        long cancellationCountBefore = cancellationRepository.count();

        BatchOperationRecord result = batchCancellationService.executeBatch(
                List.of(booking1, booking2),
                PolicyType.EMERGENCY,
                RefundScope.FULL,
                true,   // includeMovie
                true,   // includeFood
                true,   // includeVipPackage
                true,   // includeHalfPriceVoucher
                true    // includeFreeDrinkCoupon
        );

        entityManager.flush();

        // Verify batch result
        assertThat(result).isNotNull();
        assertThat(result.isPreviewOnly()).isFalse();
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isZero();

        // Verify CancellationRecords created for each booking
        long cancellationCountAfter = cancellationRepository.count();
        assertThat(cancellationCountAfter).isGreaterThanOrEqualTo(cancellationCountBefore + 2);

        // Verify audit log entries created
        long auditCountAfter = auditLogRepository.count();
        assertThat(auditCountAfter).isGreaterThan(auditCountBefore);
    }
}
