package com.eduaccess.service.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * VIP refund policy — preferential rule set for VIP members.
 * <p>
 * Rules (Task 9):
 * <ul>
 *   <li>Movie tickets:   70% refund of the original ticket price
 *       (+20 percentage points above {@link StandardPolicy}).</li>
 *   <li>Food / drinks:  100% refund.</li>
 *   <li>VIP package:    100% refund (only if included in the items).</li>
 *   <li>Voucher:        none.</li>
 * </ul>
 * Stateless and Spring-managed so the {@link CancellationPolicyFactory}
 * can auto-discover it without manual registration.
 */
@Component
public class VIPPolicy implements CancellationPolicy {

    private static final BigDecimal MOVIE_REFUND_RATE = new BigDecimal("0.70");
    private static final BigDecimal FOOD_REFUND_RATE = BigDecimal.ONE;
    private static final BigDecimal VIP_PACKAGE_REFUND_RATE = BigDecimal.ONE;

    @Override
    public PolicyType getType() {
        return PolicyType.VIP;
    }

    @Override
    public PolicyRefundResult calculate(RefundContext context) {
        PolicyRefundResult.Builder b = new PolicyRefundResult.Builder()
                .policyType(getType())
                .scope(context.scope())
                .addLine("VIP Policy: 70% movie ticket refund (+20% over standard), 100% on food.");

        if (context.includeMovie()) {
            BigDecimal movie = context.movieAmount().multiply(MOVIE_REFUND_RATE);
            b.movieRefund(movie);
            b.addLine("• Movie ticket refunded at 70% → " + format(movie));
        }
        if (context.includeFood() && context.foodAmount().signum() > 0) {
            BigDecimal food = context.foodAmount().multiply(FOOD_REFUND_RATE);
            b.foodRefund(food);
            b.addLine("• Food order refunded at 100% → " + format(food));
        }
        if (context.includeVipPackage() && context.vipPackageAmount().signum() > 0) {
            BigDecimal pkg = context.vipPackageAmount().multiply(VIP_PACKAGE_REFUND_RATE);
            b.vipPackageRefund(pkg);
            b.addLine("• VIP package refunded at 100% → " + format(pkg));
        }
        return b.build();
    }

    private static String format(BigDecimal v) {
        return "£" + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
