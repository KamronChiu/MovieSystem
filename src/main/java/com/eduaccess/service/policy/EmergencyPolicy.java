package com.eduaccess.service.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Emergency refund policy — exceptional / same-day full refunds.
 * <p>
 * Rules (Task 9):
 * <ul>
 *   <li>Movie tickets: 100% refund (overrides the normal same-day £0 rule).</li>
 *   <li>Food / drinks: 100% refund.</li>
 *   <li>VIP package:   100% refund (only if included in the items).</li>
 *   <li>Voucher:       50%-off coupon issued <em>only</em> to VIP customers,
 *       face value = 50% of the original ticket price.</li>
 * </ul>
 * Stateless and Spring-managed so the {@link CancellationPolicyFactory}
 * can auto-discover it without manual registration.
 */
@Component
public class EmergencyPolicy implements CancellationPolicy {

    private static final BigDecimal FULL_REFUND_RATE = BigDecimal.ONE;
    private static final BigDecimal PARTIAL_REFUND_RATE = new BigDecimal("0.50");
    private static final BigDecimal VIP_VOUCHER_RATE = new BigDecimal("0.50");

    @Override
    public PolicyType getType() {
        return PolicyType.EMERGENCY;
    }

    @Override
    public PolicyRefundResult calculate(RefundContext context) {
        // Emergency Policy now honours the refund scope:
        //   FULL    → 100% refund on every selected line
        //   PARTIAL → 50%  refund on every selected line
        // The PARTIAL branch is what the Batch Cancellation Dashboard
        // exposes as "Partial Refund".
        boolean isFull = context.scope() == com.eduaccess.service.policy.RefundScope.FULL;
        BigDecimal rate = isFull ? FULL_REFUND_RATE : PARTIAL_REFUND_RATE;
        String pct = isFull ? "100%" : "50%";

        PolicyRefundResult.Builder b = new PolicyRefundResult.Builder()
                .policyType(getType())
                .scope(context.scope())
                .addLine("Emergency Policy: " + pct
                        + " refund on every selected line item ("
                        + (isFull ? "full" : "partial") + " refund).");

        if (context.includeMovie()) {
            BigDecimal movie = context.movieAmount().multiply(rate);
            b.movieRefund(movie);
            b.addLine("• Movie ticket refunded at " + pct + " → " + format(movie));
        }
        if (context.includeFood() && context.foodAmount().signum() > 0) {
            BigDecimal food = context.foodAmount().multiply(rate);
            b.foodRefund(food);
            b.addLine("• Food order refunded at " + pct + " → " + format(food));
        }
        if (context.includeVipPackage() && context.vipPackageAmount().signum() > 0) {
            BigDecimal pkg = context.vipPackageAmount().multiply(rate);
            b.vipPackageRefund(pkg);
            b.addLine("• VIP package refunded at " + pct + " → " + format(pkg));
        }

        // VIP customers get a 50%-off voucher (face value = 50% of ticket price).
        // Single-cancellation flow only — the Batch Dashboard issues its own
        // compensation matrix that does not depend on the VIP flag.
        if (context.vipCustomer() && context.movieAmount().signum() > 0) {
            BigDecimal voucher = context.movieAmount().multiply(VIP_VOUCHER_RATE);
            b.voucher(voucher);
            b.addLine("• VIP compensation: 50%-off voucher issued → "
                    + format(voucher) + " face value");
        }

        return b.build();
    }

    private static String format(BigDecimal v) {
        return "£" + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
