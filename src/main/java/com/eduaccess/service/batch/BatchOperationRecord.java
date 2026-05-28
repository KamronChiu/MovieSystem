package com.eduaccess.service.batch;

import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundScope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of one full Batch Cancellation operation.
 * <p>
 * Acts as the in-memory aggregate root for the Batch Dashboard:
 * <ul>
 *   <li>{@link #getEntries()} — every per-booking outcome (success / failure).</li>
 *   <li>Header fields — operator, timestamp, chosen policy and scope.</li>
 *   <li>Roll-up totals — sum of refund amount, compensation value and voucher count.</li>
 * </ul>
 * The record is intentionally <strong>not persisted</strong> on its own
 * table — {@link com.eduaccess.service.CancellationService#submitPolicyRefund}
 * already updates each individual booking's {@code CancellationRecord} and
 * audit log, so the batch view layer only needs an in-memory aggregate to
 * render the Summary card and Email Preview dialog.
 * <p>
 * <b>Future-extension contract:</b> the {@code reserved*} hooks below leave
 * room for Batch Approvals, Manager Override and Emergency Cinema Shutdown
 * without breaking the existing API.
 */
public final class BatchOperationRecord {

    /** Random UUID, also rendered in the email preview as a tracking id. */
    private final String operationId;
    private final LocalDateTime executedAt;
    private final String operatorUsername;
    private final PolicyType policyType;
    private final RefundScope scope;
    private final boolean previewOnly;
    private final boolean includeMovie;
    private final boolean includeFood;
    private final boolean includeVipPackage;
    private final boolean includeHalfPriceVoucher;
    private final boolean includeFreeDrinkCoupon;
    private final List<BatchRefundResult> entries;

    // ── reserved future-extension hooks ───────────────────────────────────
    /** Whether a manager has approved the operation (Batch Approvals). */
    private final boolean managerApproved;
    /** Whether the operation was triggered by Emergency Cinema Shutdown. */
    private final boolean emergencyShutdown;
    /** Operator that overrode the default policy decision (Manager Override). */
    private final String managerOverrideBy;

    private final BigDecimal totalRefundAmount;
    private final BigDecimal totalCompensationValue;
    private final int totalVoucherCount;
    private final int successCount;
    private final int failureCount;

    private BatchOperationRecord(Builder b) {
        this.operationId = b.operationId == null ? UUID.randomUUID().toString() : b.operationId;
        this.executedAt = b.executedAt == null ? LocalDateTime.now() : b.executedAt;
        this.operatorUsername = b.operatorUsername == null ? "system" : b.operatorUsername;
        this.policyType = b.policyType;
        this.scope = b.scope;
        this.previewOnly = b.previewOnly;
        this.includeMovie = b.includeMovie;
        this.includeFood = b.includeFood;
        this.includeVipPackage = b.includeVipPackage;
        this.includeHalfPriceVoucher = b.includeHalfPriceVoucher;
        this.includeFreeDrinkCoupon = b.includeFreeDrinkCoupon;
        this.entries = b.entries == null
                ? List.of() : Collections.unmodifiableList(new ArrayList<>(b.entries));
        this.managerApproved = b.managerApproved;
        this.emergencyShutdown = b.emergencyShutdown;
        this.managerOverrideBy = b.managerOverrideBy;

        // Derived totals
        BigDecimal refund = BigDecimal.ZERO;
        BigDecimal comp = BigDecimal.ZERO;
        int vouchers = 0;
        int ok = 0;
        int fail = 0;
        for (BatchRefundResult e : this.entries) {
            if (e.isSuccess()) {
                refund = refund.add(e.getRefundAmount());
                comp = comp.add(e.getCompensationValue());
                vouchers += e.getVoucherCount();
                ok++;
            } else {
                fail++;
            }
        }
        this.totalRefundAmount = refund.setScale(2, RoundingMode.HALF_UP);
        this.totalCompensationValue = comp.setScale(2, RoundingMode.HALF_UP);
        this.totalVoucherCount = vouchers;
        this.successCount = ok;
        this.failureCount = fail;
    }

    public String getOperationId()             { return operationId; }
    public LocalDateTime getExecutedAt()       { return executedAt; }
    public String getOperatorUsername()        { return operatorUsername; }
    public PolicyType getPolicyType()          { return policyType; }
    public RefundScope getScope()              { return scope; }
    public boolean isPreviewOnly()             { return previewOnly; }
    public boolean isIncludeMovie()            { return includeMovie; }
    public boolean isIncludeFood()             { return includeFood; }
    public boolean isIncludeVipPackage()       { return includeVipPackage; }
    public boolean isIncludeHalfPriceVoucher() { return includeHalfPriceVoucher; }
    public boolean isIncludeFreeDrinkCoupon()  { return includeFreeDrinkCoupon; }
    public List<BatchRefundResult> getEntries(){ return entries; }
    public boolean isManagerApproved()         { return managerApproved; }
    public boolean isEmergencyShutdown()       { return emergencyShutdown; }
    public String getManagerOverrideBy()       { return managerOverrideBy; }
    public BigDecimal getTotalRefundAmount()   { return totalRefundAmount; }
    public BigDecimal getTotalCompensationValue() { return totalCompensationValue; }
    public int getTotalVoucherCount()          { return totalVoucherCount; }
    public int getSuccessCount()               { return successCount; }
    public int getFailureCount()               { return failureCount; }

    public int getTotalSelectedOrders() {
        return entries.size();
    }

    public static class Builder {
        private String operationId;
        private LocalDateTime executedAt;
        private String operatorUsername;
        private PolicyType policyType;
        private RefundScope scope;
        private boolean previewOnly;
        private boolean includeMovie = true;
        private boolean includeFood;
        private boolean includeVipPackage;
        private boolean includeHalfPriceVoucher;
        private boolean includeFreeDrinkCoupon;
        private List<BatchRefundResult> entries;
        private boolean managerApproved;
        private boolean emergencyShutdown;
        private String managerOverrideBy;

        public Builder operationId(String v)        { this.operationId = v; return this; }
        public Builder executedAt(LocalDateTime v)  { this.executedAt = v; return this; }
        public Builder operatorUsername(String v)   { this.operatorUsername = v; return this; }
        public Builder policyType(PolicyType v)     { this.policyType = v; return this; }
        public Builder scope(RefundScope v)         { this.scope = v; return this; }
        public Builder previewOnly(boolean v)       { this.previewOnly = v; return this; }
        public Builder includeMovie(boolean v)      { this.includeMovie = v; return this; }
        public Builder includeFood(boolean v)       { this.includeFood = v; return this; }
        public Builder includeVipPackage(boolean v) { this.includeVipPackage = v; return this; }
        public Builder includeHalfPriceVoucher(boolean v) { this.includeHalfPriceVoucher = v; return this; }
        public Builder includeFreeDrinkCoupon(boolean v)  { this.includeFreeDrinkCoupon = v; return this; }
        public Builder entries(List<BatchRefundResult> v) { this.entries = v; return this; }
        public Builder managerApproved(boolean v)   { this.managerApproved = v; return this; }
        public Builder emergencyShutdown(boolean v) { this.emergencyShutdown = v; return this; }
        public Builder managerOverrideBy(String v)  { this.managerOverrideBy = v; return this; }

        public BatchOperationRecord build()         { return new BatchOperationRecord(this); }
    }
}
