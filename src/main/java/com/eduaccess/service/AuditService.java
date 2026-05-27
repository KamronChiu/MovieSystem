package com.eduaccess.service;

import com.eduaccess.domain.AuditLog;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.UserAccount;
import com.eduaccess.repository.AuditRepository;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application-level service for writing and reading audit logs.
 * <p>
 * The service deliberately swallows {@link Exception} on the write path —
 * audit logging must never block or fail a business operation. Reads
 * exposed to the UI are read-only transactional operations.
 */
@Service
public class AuditService {

    /** Action constants — keep in one place to avoid typos at call sites. */
    public static final String ACTION_CANCEL_BOOKING = "CANCEL_BOOKING";
    public static final String ACTION_ADVANCE_STATUS = "ADVANCE_STATUS";
    public static final String ACTION_UPDATE_REASON = "UPDATE_REASON";
    public static final String ACTION_UPDATE_VIP = "UPDATE_VIP";

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Persists a single audit entry.
     * <p>
     * Operator and IP are pulled from the current Vaadin session/request
     * automatically — callers only need to supply the business payload.
     * Failures are caught and dropped so audit issues never propagate to
     * the caller's transaction.
     *
     * @param action          high-level operation name (see {@code ACTION_*})
     * @param targetReference booking reference or other domain key
     * @param oldStatus       status before the change (may be {@code null})
     * @param newStatus       status after the change (may be {@code null})
     * @param details         free-form note / reason
     * @return the persisted entry, or {@code null} if logging failed
     */
    @Transactional
    public AuditLog record(String action,
                           String targetReference,
                           BookingStatus oldStatus,
                           BookingStatus newStatus,
                           String details) {
        try {
            AuditLog entry = new AuditLog(
                    action,
                    resolveOperator(),
                    targetReference,
                    oldStatus,
                    newStatus,
                    resolveIpAddress(),
                    details
            );
            return auditRepository.save(entry);
        } catch (Exception ex) {
            // Audit must never break business flow.
            return null;
        }
    }

    /** Returns every audit entry, newest first — drives the Audit Log Grid. */
    @Transactional(readOnly = true)
    public List<AuditLog> findAll() {
        return auditRepository.findAllByOrderByTimestampDesc();
    }

    /** Returns audit entries filtered by action keyword. */
    @Transactional(readOnly = true)
    public List<AuditLog> findByAction(String action) {
        if (action == null || action.isBlank()) {
            return findAll();
        }
        return auditRepository.findByActionOrderByTimestampDesc(action);
    }

    /** Returns the audit trail for a single booking. */
    @Transactional(readOnly = true)
    public List<AuditLog> findByTarget(String targetReference) {
        if (targetReference == null || targetReference.isBlank()) {
            return List.of();
        }
        return auditRepository.findByTargetReferenceOrderByTimestampDesc(
                targetReference.trim().toUpperCase());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Resolves the current operator from {@link VaadinSession}.
     * Falls back to "system" when no Vaadin session is bound (e.g. when
     * called from a non-UI worker), or "anonymous" when the user is not
     * logged in.
     */
    private String resolveOperator() {
        try {
            VaadinSession session = VaadinSession.getCurrent();
            if (session == null) {
                return "system";
            }
            Object user = session.getAttribute("currentUser");
            if (user instanceof UserAccount account) {
                return account.getUsername() != null
                        ? account.getUsername()
                        : "anonymous";
            }
            return "anonymous";
        } catch (Exception ignored) {
            return "system";
        }
    }

    /**
     * Resolves the remote IP address from the current Vaadin request.
     * Returns "unknown" when no request is attached to the current
     * thread (e.g. background jobs).
     */
    private String resolveIpAddress() {
        try {
            VaadinRequest request = VaadinRequest.getCurrent();
            if (request == null) {
                return "unknown";
            }
            // Honour the X-Forwarded-For header when present (for proxies).
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return comma > 0 ? forwarded.substring(0, comma).trim()
                                 : forwarded.trim();
            }
            String remote = request.getRemoteAddr();
            return remote == null || remote.isBlank() ? "unknown" : remote;
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
