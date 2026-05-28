package com.eduaccess.service.email;

import com.eduaccess.service.compensation.CompensationItem;
import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundScope;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Immutable VO rendered by {@link com.eduaccess.ui.ReceiptPreviewDialog}.
 * <p>
 * Mirrors the printed Cancellation Receipt the operator hands to the
 * customer (TASK 12 §3). Carries:
 * <ul>
 *   <li>{@link #getBreakdownLines()} — line-by-line refund detail (movie,
 *       food, VIP package…)</li>
 *   <li>{@link #getPolicyApplied()} — full policy display name.</li>
 *   <li>{@link #getCompensationItems()} — every voucher issued.</li>
 *   <li>{@link #getOperatorUsername()} — staff member who triggered the refund.</li>
 *   <li>{@link #getTimestamp()} — when the refund was finalised.</li>
 * </ul>
 * <p>
 * Different policies produce different receipts because {@link #breakdownLines}
 * is sourced from the policy's own {@code PolicyRefundResult.getBreakdownLines()}
 * — Standard / VIP / Emergency each emit their own descriptive lines.
 */
public final class CancellationReceipt {

    private final String bookingReference;
    private final String customerName;
    private final BigDecimal refundAmount;
    private final PolicyType policyType;
    private final String policyApplied;
    private final RefundScope refundScope;
    private final List<String> breakdownLines;
    private final List<CompensationItem> compensationItems;
    private final BigDecimal compensationValue;
    private final String operatorUsername;
    private final LocalDateTime timestamp;
    private final String cinemaName;
    /** Optional batch tracking id; {@code null} for single-booking receipts. */
    private final String batchOperationId;

    private CancellationReceipt(Builder b) {
        this.bookingReference = b.bookingReference;
        this.customerName = b.customerName;
        this.refundAmount = b.refundAmount == null ? BigDecimal.ZERO : b.refundAmount;
        this.policyType = b.policyType;
        this.policyApplied = b.policyApplied;
        this.refundScope = b.refundScope;
        this.breakdownLines = b.breakdownLines == null
                ? List.of() : Collections.unmodifiableList(b.breakdownLines);
        this.compensationItems = b.compensationItems == null
                ? List.of() : Collections.unmodifiableList(b.compensationItems);
        this.compensationValue = b.compensationValue == null ? BigDecimal.ZERO : b.compensationValue;
        this.operatorUsername = b.operatorUsername == null ? "system" : b.operatorUsername;
        this.timestamp = b.timestamp == null ? LocalDateTime.now() : b.timestamp;
        this.cinemaName = b.cinemaName;
        this.batchOperationId = b.batchOperationId;
    }

    public String getBookingReference()       { return bookingReference; }
    public String getCustomerName()           { return customerName; }
    public BigDecimal getRefundAmount()       { return refundAmount; }
    public PolicyType getPolicyType()         { return policyType; }
    public String getPolicyApplied()          { return policyApplied; }
    public RefundScope getRefundScope()       { return refundScope; }
    public List<String> getBreakdownLines()   { return breakdownLines; }
    public List<CompensationItem> getCompensationItems() { return compensationItems; }
    public BigDecimal getCompensationValue()  { return compensationValue; }
    public String getOperatorUsername()       { return operatorUsername; }
    public LocalDateTime getTimestamp()       { return timestamp; }
    public String getCinemaName()             { return cinemaName; }
    public String getBatchOperationId()       { return batchOperationId; }

    public static class Builder {
        private String bookingReference;
        private String customerName;
        private BigDecimal refundAmount;
        private PolicyType policyType;
        private String policyApplied;
        private RefundScope refundScope;
        private List<String> breakdownLines;
        private List<CompensationItem> compensationItems;
        private BigDecimal compensationValue;
        private String operatorUsername;
        private LocalDateTime timestamp;
        private String cinemaName;
        private String batchOperationId;

        public Builder bookingReference(String v)       { this.bookingReference = v; return this; }
        public Builder customerName(String v)           { this.customerName = v; return this; }
        public Builder refundAmount(BigDecimal v)       { this.refundAmount = v; return this; }
        public Builder policyType(PolicyType v)         { this.policyType = v; return this; }
        public Builder policyApplied(String v)          { this.policyApplied = v; return this; }
        public Builder refundScope(RefundScope v)       { this.refundScope = v; return this; }
        public Builder breakdownLines(List<String> v)   { this.breakdownLines = v; return this; }
        public Builder compensationItems(List<CompensationItem> v) { this.compensationItems = v; return this; }
        public Builder compensationValue(BigDecimal v)  { this.compensationValue = v; return this; }
        public Builder operatorUsername(String v)       { this.operatorUsername = v; return this; }
        public Builder timestamp(LocalDateTime v)       { this.timestamp = v; return this; }
        public Builder cinemaName(String v)             { this.cinemaName = v; return this; }
        public Builder batchOperationId(String v)       { this.batchOperationId = v; return this; }

        public CancellationReceipt build() { return new CancellationReceipt(this); }
    }
}
