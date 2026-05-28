package com.eduaccess.repository;

import com.eduaccess.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link AuditLog}.
 * <p>
 * All query methods are derived from method names — no JPQL strings are
 * needed. The repository exposes a small, intentional surface: insert
 * (via {@link JpaRepository#save}), full listing newest-first, and a
 * filter-by-action helper for future drill-down views.
 */
@Repository
public interface AuditRepository extends JpaRepository<AuditLog, Long> {

    /** All audit entries, newest first — drives the Audit Log Grid. */
    List<AuditLog> findAllByOrderByTimestampDesc();

    /** All entries for a given action keyword (e.g. {@code CANCEL_BOOKING}). */
    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    /** All entries that touch a particular booking reference. */
    List<AuditLog> findByTargetReferenceOrderByTimestampDesc(String targetReference);
}
