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

/** Compatibility service for older cancellation-status code. */
@Service
public class AuditService {

    public static final String ACTION_CANCEL_BOOKING = "CANCEL_BOOKING";
    public static final String ACTION_ADVANCE_STATUS = "ADVANCE_STATUS";
    public static final String ACTION_UPDATE_REASON = "UPDATE_REASON";
    public static final String ACTION_UPDATE_VIP = "UPDATE_VIP";

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

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
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findAll() {
        return auditRepository.findAllByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByAction(String action) {
        if (action == null || action.isBlank()) {
            return findAll();
        }
        return auditRepository.findByActionOrderByTimestampDesc(action);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByTarget(String targetReference) {
        if (targetReference == null || targetReference.isBlank()) {
            return List.of();
        }
        return auditRepository.findByTargetReferenceOrderByTimestampDesc(targetReference.trim().toUpperCase());
    }

    private String resolveOperator() {
        try {
            VaadinSession session = VaadinSession.getCurrent();
            if (session == null) {
                return "system";
            }
            Object user = session.getAttribute("currentUser");
            if (user instanceof UserAccount account) {
                return account.getUsername() != null ? account.getUsername() : "anonymous";
            }
            return "anonymous";
        } catch (Exception ignored) {
            return "system";
        }
    }

    private String resolveIpAddress() {
        try {
            VaadinRequest request = VaadinRequest.getCurrent();
            if (request == null) {
                return "unknown";
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
            }
            String remote = request.getRemoteAddr();
            return remote == null || remote.isBlank() ? "unknown" : remote;
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
