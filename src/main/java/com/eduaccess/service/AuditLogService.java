package com.eduaccess.service;

import com.eduaccess.domain.AuditAction;
import com.eduaccess.domain.AuditLog;
import com.eduaccess.domain.UserAccount;
import com.eduaccess.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final LoginService loginService;

    public AuditLogService(AuditLogRepository auditLogRepository, LoginService loginService) {
        this.auditLogRepository = auditLogRepository;
        this.loginService = loginService;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findRecentLogs() {
        return auditLogRepository.findTop200ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findLogsBetween(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;

        return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay().minusNanos(1)
        );
    }

    @Transactional
    public AuditLog record(
            AuditAction action,
            String entityType,
            Long entityId,
            String reference,
            String filmTitle,
            String cinemaName,
            BigDecimal amount,
            String summary,
            String details
    ) {
        UserSnapshot actor = currentActor();

        AuditLog log = new AuditLog(
                action,
                actor.username(),
                actor.displayName(),
                actor.role(),
                entityType,
                entityId,
                reference,
                filmTitle,
                cinemaName,
                amount,
                summary,
                details
        );

        return auditLogRepository.save(log);
    }

    @Transactional
    public AuditLog record(AuditAction action, String entityType, Long entityId, String summary, String details) {
        return record(action, entityType, entityId, null, null, null, null, summary, details);
    }

    private UserSnapshot currentActor() {
        try {
            UserAccount user = loginService.getCurrentUser();
            if (user != null) {
                return new UserSnapshot(
                        safe(user.getUsername(), "unknown"),
                        safe(user.getFullName(), user.getUsername()),
                        user.getRole() == null ? "UNKNOWN" : user.getRole().name()
                );
            }
        } catch (RuntimeException ignored) {
            // This can happen during startup/background work where there is no Vaadin session.
        }

        return new UserSnapshot("system", "System", "SYSTEM");
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record UserSnapshot(String username, String displayName, String role) {
    }
}
