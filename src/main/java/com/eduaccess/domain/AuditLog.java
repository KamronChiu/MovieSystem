package com.eduaccess.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "operation_audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 60)
    private AuditAction action;

    @Column(name = "actor_username")
    private String actorUsername;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "reference")
    private String reference;

    @Column(name = "target_reference")
    private String targetReference;

    @Column(name = "film_title")
    private String filmTitle;

    @Column(name = "cinema_name")
    private String cinemaName;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "details", length = 2500)
    private String details;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Legacy compatibility fields used by older AuditService/AuditRepository code.
    @Column(name = "operator")
    private String operator;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "old_status")
    @Enumerated(EnumType.STRING)
    private BookingStatus oldStatus;

    @Column(name = "new_status")
    @Enumerated(EnumType.STRING)
    private BookingStatus newStatus;

    protected AuditLog() {
    }

    public AuditLog(
            AuditAction action,
            String actorUsername,
            String actorName,
            String actorRole,
            String entityType,
            Long entityId,
            String reference,
            String filmTitle,
            String cinemaName,
            BigDecimal amount,
            String summary,
            String details
    ) {
        this.action = action == null ? AuditAction.SYSTEM_EVENT : action;
        this.actorUsername = safe(actorUsername, "system");
        this.actorName = safe(actorName, this.actorUsername);
        this.actorRole = safe(actorRole, "SYSTEM");
        this.entityType = safe(entityType, "Unknown");
        this.entityId = entityId;
        this.reference = reference;
        this.targetReference = reference;
        this.filmTitle = filmTitle;
        this.cinemaName = cinemaName;
        this.amount = amount;
        this.summary = safe(summary, this.action.getLabel());
        this.details = details;
        this.createdAt = LocalDateTime.now();
        this.timestamp = this.createdAt;
        this.operator = this.actorUsername;
        this.ipAddress = "unknown";
    }

    public AuditLog(
            String action,
            String operator,
            String targetReference,
            BookingStatus oldStatus,
            BookingStatus newStatus,
            String ipAddress,
            String details
    ) {
        this.action = AuditAction.fromCode(action);
        this.actorUsername = safe(operator, "system");
        this.actorName = this.actorUsername;
        this.actorRole = "SYSTEM";
        this.operator = this.actorUsername;
        this.targetReference = targetReference;
        this.reference = targetReference;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.ipAddress = safe(ipAddress, "unknown");
        this.entityType = "Booking";
        this.summary = this.action.getLabel();
        this.details = details;
        this.createdAt = LocalDateTime.now();
        this.timestamp = this.createdAt;
    }

    @PrePersist
    @PreUpdate
    private void fillDefaults() {
        if (action == null) {
            action = AuditAction.SYSTEM_EVENT;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (timestamp == null) {
            timestamp = createdAt;
        }
        if (actorUsername == null || actorUsername.isBlank()) {
            actorUsername = "system";
        }
        if (actorName == null || actorName.isBlank()) {
            actorName = actorUsername;
        }
        if (actorRole == null || actorRole.isBlank()) {
            actorRole = "SYSTEM";
        }
        if (operator == null || operator.isBlank()) {
            operator = actorUsername;
        }
        if (entityType == null || entityType.isBlank()) {
            entityType = "Unknown";
        }
        if (summary == null || summary.isBlank()) {
            summary = action.getLabel();
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = "unknown";
        }
        if ((reference == null || reference.isBlank()) && targetReference != null) {
            reference = targetReference;
        }
        if ((targetReference == null || targetReference.isBlank()) && reference != null) {
            targetReference = reference;
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public Long getId() { return id; }
    public AuditAction getAction() { return action; }
    public String getActorUsername() { return actorUsername; }
    public String getActorName() { return actorName; }
    public String getActorRole() { return actorRole; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getReference() { return reference; }
    public String getTargetReference() { return targetReference; }
    public String getFilmTitle() { return filmTitle; }
    public String getCinemaName() { return cinemaName; }
    public BigDecimal getAmount() { return amount; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getOperator() { return operator; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getIpAddress() { return ipAddress; }
    public BookingStatus getOldStatus() { return oldStatus; }
    public BookingStatus getNewStatus() { return newStatus; }

    public void setAction(AuditAction action) { this.action = action; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public void setReference(String reference) { this.reference = reference; this.targetReference = reference; }
    public void setTargetReference(String targetReference) { this.targetReference = targetReference; this.reference = targetReference; }
    public void setFilmTitle(String filmTitle) { this.filmTitle = filmTitle; }
    public void setCinemaName(String cinemaName) { this.cinemaName = cinemaName; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setDetails(String details) { this.details = details; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; this.timestamp = createdAt; }
    public void setOperator(String operator) { this.operator = operator; this.actorUsername = operator; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; this.createdAt = timestamp; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setOldStatus(BookingStatus oldStatus) { this.oldStatus = oldStatus; }
    public void setNewStatus(BookingStatus newStatus) { this.newStatus = newStatus; }
}
