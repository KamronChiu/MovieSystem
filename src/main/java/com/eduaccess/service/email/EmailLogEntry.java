package com.eduaccess.service.email;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row in the Email Management dashboard.
 * <p>
 * Captures a snapshot of every {@link CancellationEmail} (and its paired
 * {@link CancellationReceipt}) ever produced by {@link EmailReceiptService},
 * along with a mutable delivery {@link Status} the operator can flip from
 * the management view.
 * <p>
 * The entry is kept in memory by {@link EmailLogService} — Email Management
 * is a simulation surface (TASK 12), not a real outbound mail pipeline.
 */
public final class EmailLogEntry {

    public enum Source {
        SINGLE("Single cancellation"),
        BATCH("Batch cancellation");

        private final String displayName;
        Source(String name) { this.displayName = name; }
        public String getDisplayName() { return displayName; }
    }

    public enum Status {
        SENT("Sent", "#16a34a"),
        PENDING("Pending", "#f59e0b"),
        FAILED("Failed", "#dc2626");

        private final String displayName;
        private final String color;
        Status(String name, String color) { this.displayName = name; this.color = color; }
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }

    private final String id;
    private final LocalDateTime createdAt;
    private final Source source;
    private final String batchOperationId;     // null for single
    private final CancellationEmail email;
    private final CancellationReceipt receipt;
    private final String customerName;
    private final String customerEmail;
    private final String bookingReference;
    private final String subject;
    private final BigDecimal refundAmount;
    private final String templateKey;          // which template was applied

    private Status status;
    private LocalDateTime statusUpdatedAt;
    private String statusNote;

    public EmailLogEntry(Source source,
                         String batchOperationId,
                         CancellationEmail email,
                         CancellationReceipt receipt,
                         String templateKey,
                         Status initialStatus) {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.source = source;
        this.batchOperationId = batchOperationId;
        this.email = email;
        this.receipt = receipt;
        this.customerName = email == null ? "" : email.getCustomerName();
        this.customerEmail = email == null ? "" : email.getTo();
        this.bookingReference = email == null ? "" : email.getBookingReference();
        this.subject = email == null ? "" : email.getSubject();
        this.refundAmount = email == null ? BigDecimal.ZERO : email.getRefundAmount();
        this.templateKey = templateKey;
        this.status = initialStatus == null ? Status.SENT : initialStatus;
        this.statusUpdatedAt = this.createdAt;
        this.statusNote = "";
    }

    public String getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Source getSource() { return source; }
    public String getBatchOperationId() { return batchOperationId; }
    public CancellationEmail getEmail() { return email; }
    public CancellationReceipt getReceipt() { return receipt; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getBookingReference() { return bookingReference; }
    public String getSubject() { return subject; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public String getTemplateKey() { return templateKey; }

    public Status getStatus() { return status; }
    public LocalDateTime getStatusUpdatedAt() { return statusUpdatedAt; }
    public String getStatusNote() { return statusNote; }

    public void updateStatus(Status next, String note) {
        if (next == null) return;
        this.status = next;
        this.statusUpdatedAt = LocalDateTime.now();
        this.statusNote = note == null ? "" : note;
    }
}
