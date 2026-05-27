package com.eduaccess.repository;

import com.eduaccess.domain.CancellationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CancellationRecord}.
 * <p>
 * Required by TASK 6 — CancellationRecord Persistence.
 * Lookups are keyed on {@code bookingReference} since each booking has at
 * most one cancellation record (one-to-one by business semantics).
 */
@Repository
public interface CancellationRepository extends JpaRepository<CancellationRecord, Long> {

    /**
     * Finds the record for a given booking reference.
     *
     * @param bookingReference business key of the booking
     * @return the record if present
     */
    Optional<CancellationRecord> findByBookingReference(String bookingReference);

    /**
     * Returns whether a record already exists for the given booking reference.
     *
     * @param bookingReference business key of the booking
     * @return {@code true} when a record is already persisted
     */
    boolean existsByBookingReference(String bookingReference);

    /**
     * Returns every cancellation record ordered by most recent first.
     * <p>
     * Useful for an administrative audit list.
     *
     * @return all records, newest first
     */
    List<CancellationRecord> findAllByOrderByCancelledAtDesc();
}
