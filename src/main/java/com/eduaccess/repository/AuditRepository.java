package com.eduaccess.repository;

import com.eduaccess.domain.AuditAction;
import com.eduaccess.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Compatibility repository for older cancellation audit code.
 *
 * The new manager audit feature uses AuditLogRepository. This repository is kept
 * so older services that still inject AuditRepository compile and run safely.
 */
public interface AuditRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByTimestampDesc();

    default List<AuditLog> findByActionOrderByTimestampDesc(String action) {
        AuditAction target = AuditAction.fromCode(action);
        return findAllByOrderByTimestampDesc().stream()
                .filter(log -> log.getAction() == target)
                .toList();
    }

    default List<AuditLog> findByTargetReferenceOrderByTimestampDesc(String targetReference) {
        if (targetReference == null || targetReference.isBlank()) {
            return List.of();
        }
        String expected = targetReference.trim().toUpperCase();
        return findAllByOrderByTimestampDesc().stream()
                .filter(log -> log.getTargetReference() != null)
                .filter(log -> log.getTargetReference().trim().toUpperCase().equals(expected))
                .toList();
    }
}
