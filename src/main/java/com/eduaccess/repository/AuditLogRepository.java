package com.eduaccess.repository;

import com.eduaccess.domain.AuditAction;
import com.eduaccess.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    List<AuditLog> findTop200ByOrderByCreatedAtDesc();

    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    List<AuditLog> findByActionInAndCreatedAtBetweenOrderByCreatedAtDesc(
            Collection<AuditAction> actions,
            LocalDateTime start,
            LocalDateTime end
    );

    List<AuditLog> findByReferenceOrderByCreatedAtDesc(String reference);
}
