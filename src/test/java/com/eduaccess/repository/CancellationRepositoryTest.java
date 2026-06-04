package com.eduaccess.repository;

import com.eduaccess.domain.CancellationRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CancellationRepository}.
 * <p>
 * Verifies the custom finder methods and ordering for cancellation records.
 */
@DataJpaTest
class CancellationRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CancellationRepository cancellationRepository;

    @Test
    @DisplayName("findByBookingReference_returnsCorrectRecord")
    void findByBookingReference_returnsCorrectRecord() {
        CancellationRecord record = new CancellationRecord(
                "HCBS-CR-001",
                new BigDecimal("15.00"),
                "Customer changed plans",
                LocalDateTime.now(),
                false
        );
        em.persist(record);
        em.flush();

        Optional<CancellationRecord> found =
                cancellationRepository.findByBookingReference("HCBS-CR-001");

        assertThat(found).isPresent();
        assertThat(found.get().getRefundAmount()).isEqualByComparingTo("15.00");
        assertThat(found.get().getCancellationReason()).isEqualTo("Customer changed plans");
        assertThat(found.get().isRefunded()).isFalse();
    }

    @Test
    @DisplayName("findByBookingReference_notFound_returnsEmpty")
    void findByBookingReference_notFound_returnsEmpty() {
        Optional<CancellationRecord> found =
                cancellationRepository.findByBookingReference("DOES-NOT-EXIST");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByBookingReference_exists_returnsTrue")
    void existsByBookingReference_exists_returnsTrue() {
        CancellationRecord record = new CancellationRecord(
                "HCBS-CR-002",
                new BigDecimal("10.00"),
                "",
                LocalDateTime.now(),
                true
        );
        em.persist(record);
        em.flush();

        assertThat(cancellationRepository.existsByBookingReference("HCBS-CR-002")).isTrue();
        assertThat(cancellationRepository.existsByBookingReference("HCBS-CR-NONE")).isFalse();
    }

    @Test
    @DisplayName("findAllByOrderByCancelledAtDesc_ordersNewestFirst")
    void findAllByOrderByCancelledAtDesc_ordersNewestFirst() {
        LocalDateTime earlier = LocalDateTime.of(2025, 5, 1, 10, 0);
        LocalDateTime later = LocalDateTime.of(2025, 5, 28, 14, 0);

        CancellationRecord older = new CancellationRecord(
                "HCBS-CR-OLD", BigDecimal.ZERO, "", earlier, false);
        CancellationRecord newer = new CancellationRecord(
                "HCBS-CR-NEW", new BigDecimal("20.00"), "reason", later, true);
        em.persist(older);
        em.persist(newer);
        em.flush();

        List<CancellationRecord> all = cancellationRepository.findAllByOrderByCancelledAtDesc();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).getBookingReference()).isEqualTo("HCBS-CR-NEW");
        assertThat(all.get(1).getBookingReference()).isEqualTo("HCBS-CR-OLD");
    }
}
