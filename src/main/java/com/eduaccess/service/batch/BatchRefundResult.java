package com.eduaccess.service.batch;

import com.eduaccess.service.compensation.CompensationItem;
import com.eduaccess.service.compensation.CompensationPackage;
import com.eduaccess.service.policy.PolicyRefundResult;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Per-booking outcome inside a {@link BatchOperationRecord}.
 * <p>
 * Carries every datum the Batch Dashboard, Email Preview and Refund
 * History need to render. Successful entries expose the policy
 * breakdown and compensation package; failed entries expose the
 * error message so the operator can retry the booking individually.
 * <p>
 * Immutable — never mutated after construction.
 */
public final class BatchRefundResult {

    private final String bookingReference;
    private final String customerName;
    private final String customerEmail;
    private final boolean success;
    private final boolean vipCustomer;
    private final BigDecimal refundAmount;
    private final BigDecimal compensationValue;
    private final int voucherCount;
    private final List<String> breakdownLines;
    private final List<CompensationItem> compensationItems;
    private final String errorMessage;

    private BatchRefundResult(Builder b) {
        this.bookingReference = b.bookingReference;
        this.customerName = b.customerName;
        this.customerEmail = b.customerEmail;
        this.success = b.success;
        this.vipCustomer = b.vipCustomer;
        this.refundAmount = b.refundAmount == null ? BigDecimal.ZERO : b.refundAmount;
        this.compensationValue = b.compensationValue == null ? BigDecimal.ZERO : b.compensationValue;
        this.voucherCount = b.voucherCount;
        this.breakdownLines = b.breakdownLines == null
                ? List.of() : Collections.unmodifiableList(b.breakdownLines);
        this.compensationItems = b.compensationItems == null
                ? List.of() : Collections.unmodifiableList(b.compensationItems);
        this.errorMessage = b.errorMessage;
    }

    public String getBookingReference()       { return bookingReference; }
    public String getCustomerName()           { return customerName; }
    public String getCustomerEmail()          { return customerEmail; }
    public boolean isSuccess()                { return success; }
    public boolean isVipCustomer()            { return vipCustomer; }
    public BigDecimal getRefundAmount()       { return refundAmount; }
    public BigDecimal getCompensationValue()  { return compensationValue; }
    public int getVoucherCount()              { return voucherCount; }
    public List<String> getBreakdownLines()   { return breakdownLines; }
    public List<CompensationItem> getCompensationItems() { return compensationItems; }
    public String getErrorMessage()           { return errorMessage; }

    /** Convenience factory — wires {@link PolicyRefundResult} + {@link CompensationPackage} into one row. */
    public static BatchRefundResult success(String ref,
                                            String customerName,
                                            String customerEmail,
                                            boolean vipCustomer,
                                            PolicyRefundResult policy,
                                            CompensationPackage pkg) {
        return new Builder()
                .bookingReference(ref)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .vipCustomer(vipCustomer)
                .success(true)
                .refundAmount(policy == null ? BigDecimal.ZERO : policy.getFinalRefund())
                .compensationValue(pkg == null ? BigDecimal.ZERO : pkg.getTotalValue())
                .voucherCount(pkg == null ? 0 : pkg.getItems().size())
                .breakdownLines(policy == null ? List.of() : policy.getBreakdownLines())
                .compensationItems(pkg == null ? List.of() : pkg.getItems())
                .build();
    }

    public static BatchRefundResult failure(String ref,
                                            String customerName,
                                            String customerEmail,
                                            String errorMessage) {
        return new Builder()
                .bookingReference(ref)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static class Builder {
        private String bookingReference;
        private String customerName;
        private String customerEmail;
        private boolean success;
        private boolean vipCustomer;
        private BigDecimal refundAmount;
        private BigDecimal compensationValue;
        private int voucherCount;
        private List<String> breakdownLines;
        private List<CompensationItem> compensationItems;
        private String errorMessage;

        public Builder bookingReference(String v) { this.bookingReference = v; return this; }
        public Builder customerName(String v)     { this.customerName = v; return this; }
        public Builder customerEmail(String v)    { this.customerEmail = v; return this; }
        public Builder success(boolean v)         { this.success = v; return this; }
        public Builder vipCustomer(boolean v)     { this.vipCustomer = v; return this; }
        public Builder refundAmount(BigDecimal v) { this.refundAmount = v; return this; }
        public Builder compensationValue(BigDecimal v) { this.compensationValue = v; return this; }
        public Builder voucherCount(int v)        { this.voucherCount = v; return this; }
        public Builder breakdownLines(List<String> v) { this.breakdownLines = v; return this; }
        public Builder compensationItems(List<CompensationItem> v) { this.compensationItems = v; return this; }
        public Builder errorMessage(String v)     { this.errorMessage = v; return this; }

        public BatchRefundResult build() { return new BatchRefundResult(this); }
    }
}
