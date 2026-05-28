package com.eduaccess.service.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable result of running a {@link CancellationPolicy}.
 * <p>
 * Mirrors the line items rendered in the Refund Breakdown card on
 * the Refund Pending step:
 * <ul>
 *   <li>{@code movieRefund} — refund attributable to the ticket.</li>
 *   <li>{@code foodRefund} — refund for food orders attached to the booking.</li>
 *   <li>{@code vipPackageRefund} — refund for the VIP membership add-on.</li>
 *   <li>{@code voucher} — bonus voucher face value (Emergency + VIP only).</li>
 *   <li>{@code finalRefund} — sum of the three refund line items
 *       (the voucher is shown separately, not added).</li>
 *   <li>{@code breakdownLines} — human-readable rule descriptions.</li>
 * </ul>
 * Build via {@link Builder}; instances themselves are unmodifiable.
 */
public final class PolicyRefundResult {

    private static final int MONEY_SCALE = 2;

    private final PolicyType policyType;
    private final RefundScope scope;
    private final BigDecimal movieRefund;
    private final BigDecimal foodRefund;
    private final BigDecimal vipPackageRefund;
    private final BigDecimal voucher;
    private final BigDecimal finalRefund;
    private final List<String> breakdownLines;

    private PolicyRefundResult(Builder b) {
        this.policyType = b.policyType;
        this.scope = b.scope;
        this.movieRefund = scale(b.movieRefund);
        this.foodRefund = scale(b.foodRefund);
        this.vipPackageRefund = scale(b.vipPackageRefund);
        this.voucher = scale(b.voucher);
        this.finalRefund = scale(b.movieRefund.add(b.foodRefund).add(b.vipPackageRefund));
        this.breakdownLines = List.copyOf(b.breakdownLines);
    }

    public PolicyType getPolicyType() {
        return policyType;
    }

    public RefundScope getScope() {
        return scope;
    }

    public BigDecimal getMovieRefund() {
        return movieRefund;
    }

    public BigDecimal getFoodRefund() {
        return foodRefund;
    }

    public BigDecimal getVipPackageRefund() {
        return vipPackageRefund;
    }

    public BigDecimal getVoucher() {
        return voucher;
    }

    public BigDecimal getFinalRefund() {
        return finalRefund;
    }

    public List<String> getBreakdownLines() {
        return breakdownLines;
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static class Builder {
        private PolicyType policyType;
        private RefundScope scope;
        private BigDecimal movieRefund = BigDecimal.ZERO;
        private BigDecimal foodRefund = BigDecimal.ZERO;
        private BigDecimal vipPackageRefund = BigDecimal.ZERO;
        private BigDecimal voucher = BigDecimal.ZERO;
        private final List<String> breakdownLines = new ArrayList<>();

        public Builder policyType(PolicyType policyType) {
            this.policyType = policyType;
            return this;
        }

        public Builder scope(RefundScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder movieRefund(BigDecimal v) {
            this.movieRefund = v == null ? BigDecimal.ZERO : v;
            return this;
        }

        public Builder foodRefund(BigDecimal v) {
            this.foodRefund = v == null ? BigDecimal.ZERO : v;
            return this;
        }

        public Builder vipPackageRefund(BigDecimal v) {
            this.vipPackageRefund = v == null ? BigDecimal.ZERO : v;
            return this;
        }

        public Builder voucher(BigDecimal v) {
            this.voucher = v == null ? BigDecimal.ZERO : v;
            return this;
        }

        public Builder addLine(String line) {
            this.breakdownLines.add(line);
            return this;
        }

        public PolicyRefundResult build() {
            return new PolicyRefundResult(this);
        }
    }
}
