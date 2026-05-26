package com.eduaccess.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable summary of a refund calculation.
 * <p>
 * Contains the original booking amount, the actual refund amount,
 * the fee (deduction) amount, and a list of human-readable
 * adjustment descriptions that explain how the final figures
 * were derived.
 * <p>
 * Instances are created exclusively by {@link com.eduaccess.service.RefundCalculator}
 * and consumed by the service layer and UI for display purposes.
 * The UI must never perform refund calculations itself.
 */
public final class RefundSummary {

    private final BigDecimal originalAmount;
    private final BigDecimal refundAmount;
    private final BigDecimal feeAmount;
    private final List<String> adjustments;

    public RefundSummary(
            BigDecimal originalAmount,
            BigDecimal refundAmount,
            BigDecimal feeAmount,
            List<String> adjustments
    ) {
        this.originalAmount = originalAmount;
        this.refundAmount = refundAmount;
        this.feeAmount = feeAmount;
        this.adjustments = List.copyOf(adjustments);
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    /**
     * Returns an unmodifiable list of adjustment descriptions.
     * Each entry describes one adjustment factor applied during
     * the refund calculation (e.g. "VIP bonus: +20%",
     * "Weekend surcharge: −10%", "Same-day cancellation: 0% refund").
     *
     * @return human-readable adjustment descriptions
     */
    public List<String> getAdjustments() {
        return adjustments;
    }

    /**
     * Builder for constructing a {@link RefundSummary} step-by-step.
     * <p>
     * Allows the calculator to record adjustments incrementally
     * and then produce the final immutable summary.
     */
    public static class Builder {

        private BigDecimal originalAmount;
        private BigDecimal refundAmount;
        private BigDecimal feeAmount;
        private final List<String> adjustments = new ArrayList<>();

        public Builder originalAmount(BigDecimal originalAmount) {
            this.originalAmount = originalAmount;
            return this;
        }

        public Builder refundAmount(BigDecimal refundAmount) {
            this.refundAmount = refundAmount;
            return this;
        }

        public Builder feeAmount(BigDecimal feeAmount) {
            this.feeAmount = feeAmount;
            return this;
        }

        public Builder addAdjustment(String description) {
            this.adjustments.add(description);
            return this;
        }

        public RefundSummary build() {
            return new RefundSummary(originalAmount, refundAmount, feeAmount, adjustments);
        }
    }
}