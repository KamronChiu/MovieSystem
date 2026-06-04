package com.eduaccess.service.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Standard refund policy — the default rule set applied to ordinary
 * cancellations (Partial Refund + non-VIP customers).
 * <p>
 * Rules (Task 9):
 * <ul>
 *   <li>Movie tickets:   50% refund of the original ticket price.</li>
 *   <li>Food / drinks:  100% refund.</li>
 *   <li>VIP package:    100% refund (only if included in the items).</li>
 *   <li>Voucher:        none.</li>
 *   <li><b>Same-day/past screening: £0 refund (overrides the 50% rule).</b></li>
 * </ul>
 * <p>
 * 标准退款策略 — 适用于普通取消的默认规则集（部分退款 + 非VIP客户）。
 * <p>
 * 规则（任务9）：
 * <ul>
 *   <li>电影票：原始票价的50%退款。</li>
 *   <li>食品/饮料：100%退款。</li>
 *   <li>VIP套餐：100%退款（仅在包含此项目时）。</li>
 *   <li>优惠券：无。</li>
 *   <li><b>同日/已放映场次：0元退款（覆盖50%规则）。</b></li>
 * </ul>
 * Stateless and Spring-managed so the {@link CancellationPolicyFactory}
 * can auto-discover it without manual registration.
 * <p>
 * 无状态且由Spring管理，因此 {@link CancellationPolicyFactory}
 * 可以自动发现它，无需手动注册。
 */
@Component
public class StandardPolicy implements CancellationPolicy {

    private static final BigDecimal MOVIE_REFUND_RATE = new BigDecimal("0.50");
    private static final BigDecimal FOOD_REFUND_RATE = BigDecimal.ONE;
    private static final BigDecimal VIP_PACKAGE_REFUND_RATE = BigDecimal.ONE;

    @Override
    public PolicyType getType() {
        return PolicyType.STANDARD;
    }

    @Override
    public PolicyRefundResult calculate(RefundContext context) {
        // ── Same-day / past screening check ──────────────────────────────
        // 同日/已放映场次检查
        // For same-day or past screenings, movie ticket refund is £0.
        // 对于同日或已放映的场次，电影票退款为0元。
        // This rule overrides the standard 50% refund rate.
        // 此规则覆盖标准的50%退款率。
        boolean isSameDayOrPast = isSameDayOrPastScreening(context);

        PolicyRefundResult.Builder b = new PolicyRefundResult.Builder()
                .policyType(getType())
                .scope(context.scope())
                .addLine("Standard Policy: 50% movie ticket refund, 100% on food.");

        // Add same-day warning if applicable
        // 如果适用，添加同日取消警告
        if (isSameDayOrPast) {
            b.addLine("• ⚠️ Same-day/past screening: Movie ticket refund → £0.00");
        }

        // Movie ticket refund: 0% for same-day/past, 50% for future screenings
        // 电影票退款：同日/已放映为0%，未来场次为50%
        if (context.includeMovie()) {
            BigDecimal movieRate = isSameDayOrPast ? BigDecimal.ZERO : MOVIE_REFUND_RATE;
            BigDecimal movie = context.movieAmount().multiply(movieRate);
            b.movieRefund(movie);
            if (!isSameDayOrPast) {
                b.addLine("• Movie ticket refunded at 50% → " + format(movie));
            }
        }

        // Food refund: always 100% (not affected by screening date)
        // 食品退款：始终100%（不受放映日期影响）
        if (context.includeFood() && context.foodAmount().signum() > 0) {
            BigDecimal food = context.foodAmount().multiply(FOOD_REFUND_RATE);
            b.foodRefund(food);
            b.addLine("• Food order refunded at 100% → " + format(food));
        }

        // VIP package refund: always 100% (not affected by screening date)
        // VIP套餐退款：始终100%（不受放映日期影响）
        if (context.includeVipPackage() && context.vipPackageAmount().signum() > 0) {
            BigDecimal pkg = context.vipPackageAmount().multiply(VIP_PACKAGE_REFUND_RATE);
            b.vipPackageRefund(pkg);
            b.addLine("• VIP package refunded at 100% → " + format(pkg));
        }
        return b.build();
    }

    /**
     * Checks if the screening date is today or in the past.
     * 检查放映日期是否为今天或之前。
     *
     * @param context the refund context containing screening date info
     *                包含放映日期信息的退款上下文
     * @return true if the screening is same-day or already completed
     *         如果场次为同日或已完成则返回true
     */
    private boolean isSameDayOrPastScreening(RefundContext context) {
        if (context.screeningDate() == null) {
            return false; // Safety check: if no date, assume future screening
            // 安全检查：如果没有日期，假设为未来场次
        }
        LocalDate today = LocalDate.now();
        // Return true if screening date is today or earlier (already shown)
        // 如果放映日期是今天或更早（已放映），返回true
        return !context.screeningDate().isAfter(today);
    }

    private static String format(BigDecimal v) {
        return "£" + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
