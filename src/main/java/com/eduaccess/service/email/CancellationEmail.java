package com.eduaccess.service.email;

import com.eduaccess.service.compensation.CompensationItem;
import com.eduaccess.service.policy.PolicyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Immutable VO rendered by {@link com.eduaccess.ui.EmailPreviewDialog}.
 * <p>
 * One {@link CancellationEmail} is produced for each cancelled booking
 * (single flow) or for each successful row of a batch operation. The VO
 * carries every datum the email body needs so the dialog never has to
 * call back into a service.
 *
 * <p>Mandatory fields (TASK 12 §1):
 * <ul>
 *   <li>{@link #getBookingReference()} — short tracking id shown in the subject.</li>
 *   <li>{@link #getRefundAmount()} — final monetary refund.</li>
 *   <li>{@link #getPolicyName()} — human-readable policy applied.</li>
 *   <li>{@link #getCompensationItems()} — every voucher / coupon issued.</li>
 *   <li>{@link #getCancellationTime()} — timestamp the operation took place.</li>
 * </ul>
 */
public final class CancellationEmail {

    public enum Kind { SINGLE, BATCH_EMERGENCY }

    private final Kind kind;
    private final String to;
    private final String customerName;
    private final String subject;
    private final String bookingReference;
    private final PolicyType policyType;
    private final String policyName;
    private final BigDecimal refundAmount;
    private final BigDecimal compensationValue;
    private final List<CompensationItem> compensationItems;
    private final LocalDateTime cancellationTime;
    private final String cinemaName;
    private final List<String> bodyLines;

    private CancellationEmail(Builder b) {
        this.kind = b.kind == null ? Kind.SINGLE : b.kind;
        this.to = b.to == null ? "customer@hcbs-cinema.example" : b.to;
        this.customerName = b.customerName == null ? "Valued Customer" : b.customerName;
        this.subject = b.subject;
        this.bookingReference = b.bookingReference;
        this.policyType = b.policyType;
        this.policyName = b.policyName;
        this.refundAmount = b.refundAmount == null ? BigDecimal.ZERO : b.refundAmount;
        this.compensationValue = b.compensationValue == null ? BigDecimal.ZERO : b.compensationValue;
        this.compensationItems = b.compensationItems == null
                ? List.of() : Collections.unmodifiableList(b.compensationItems);
        this.cancellationTime = b.cancellationTime == null
                ? LocalDateTime.now() : b.cancellationTime;
        this.cinemaName = b.cinemaName;
        this.bodyLines = b.bodyLines == null
                ? List.of() : Collections.unmodifiableList(b.bodyLines);
    }

    public Kind getKind()                              { return kind; }
    public String getTo()                              { return to; }
    public String getCustomerName()                    { return customerName; }
    public String getSubject()                         { return subject; }
    public String getBookingReference()                { return bookingReference; }
    public PolicyType getPolicyType()                  { return policyType; }
    public String getPolicyName()                      { return policyName; }
    public BigDecimal getRefundAmount()                { return refundAmount; }
    public BigDecimal getCompensationValue()           { return compensationValue; }
    public List<CompensationItem> getCompensationItems() { return compensationItems; }
    public LocalDateTime getCancellationTime()         { return cancellationTime; }
    public String getCinemaName()                      { return cinemaName; }
    public List<String> getBodyLines()                 { return bodyLines; }

    public static class Builder {
        private Kind kind;
        private String to;
        private String customerName;
        private String subject;
        private String bookingReference;
        private PolicyType policyType;
        private String policyName;
        private BigDecimal refundAmount;
        private BigDecimal compensationValue;
        private List<CompensationItem> compensationItems;
        private LocalDateTime cancellationTime;
        private String cinemaName;
        private List<String> bodyLines;

        public Builder kind(Kind v)                              { this.kind = v; return this; }
        public Builder to(String v)                              { this.to = v; return this; }
        public Builder customerName(String v)                    { this.customerName = v; return this; }
        public Builder subject(String v)                         { this.subject = v; return this; }
        public Builder bookingReference(String v)                { this.bookingReference = v; return this; }
        public Builder policyType(PolicyType v)                  { this.policyType = v; return this; }
        public Builder policyName(String v)                      { this.policyName = v; return this; }
        public Builder refundAmount(BigDecimal v)                { this.refundAmount = v; return this; }
        public Builder compensationValue(BigDecimal v)           { this.compensationValue = v; return this; }
        public Builder compensationItems(List<CompensationItem> v) { this.compensationItems = v; return this; }
        public Builder cancellationTime(LocalDateTime v)         { this.cancellationTime = v; return this; }
        public Builder cinemaName(String v)                      { this.cinemaName = v; return this; }
        public Builder bodyLines(List<String> v)                 { this.bodyLines = v; return this; }

        public CancellationEmail build() { return new CancellationEmail(this); }
    }
}
