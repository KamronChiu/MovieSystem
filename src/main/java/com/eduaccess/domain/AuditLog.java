package com.eduaccess.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Persistent audit-log entry.
 * <p>
 * Captures every meaningful state-changing operation in the cancellation
 * pipeline so that operators have a complete, immutable trail of who did
 * what, when, and from where.
 * <p>
 * The schema is intentionally generic — {@link #action} is a free-form
 * string (e.g. {@code "CANCEL_BOOKING"}, {@code "ADVANCE_STATUS"}) so the
 * same table can be reused by other modules in the future without DDL
 * changes. The status columns are mapped as {@link BookingStatus} enums
 * but stored as strings, so they remain meaningful even if the enum is
 * extended later.
 */
@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
                @Index(name = "idx_audit_action", columnList = "action"),
                @Index(name = "idx_audit_target", columnList = "target_reference")
        })
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** High-level operation name, e.g. {@code CANCEL_BOOKING}. */
    @Column(name = "action", nullable = false, length = 80)
    private String action;

    /** Username (or "anonymous") of the user that triggered the action. */
    @Column(name = "operator", nullable = false, length = 120)
    private String operator;

    /**
     * Optional booking reference (or other domain key) the action targets.
     * Kept loose-coupled (string) so this table can also log non-booking
     * events later without a schema change.
     */
    @Column(name = "target_reference", length = 80)
    private String targetReference;

    @Column(name = "old_status", length = 40)
    @Enumerated(EnumType.STRING)
    private BookingStatus oldStatus;

    @Column(name = "new_status", length = 40)
    @Enumerated(EnumType.STRING)
    private BookingStatus newStatus;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** Optional human-readable note (e.g. cancellation reason). */
    @Column(name = "details", length = 500)
    private String details;

    public AuditLog() {
    }

    public AuditLog(String action,
                    String operator,
                    String targetReference,
                    BookingStatus oldStatus,
                    BookingStatus newStatus,
                    String ipAddress,
                    String details) {
        this.action = action;
        this.operator = operator;
        this.targetReference = targetReference;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.ipAddress = ipAddress;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Defensive default-value hook fired by JPA right before {@code INSERT}.
     * Guarantees NOT-NULL columns always have a value even when callers
     * forget to set them explicitly.
     */
    @PrePersist
    private void ensureDefaults() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (operator == null || operator.isBlank()) {
            operator = "anonymous";
        }
        if (action == null || action.isBlank()) {
            action = "UNKNOWN";
        }
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getTargetReference() {
        return targetReference;
    }

    public void setTargetReference(String targetReference) {
        this.targetReference = targetReference;
    }

    public BookingStatus getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(BookingStatus oldStatus) {
        this.oldStatus = oldStatus;
    }

    public BookingStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(BookingStatus newStatus) {
        this.newStatus = newStatus;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
