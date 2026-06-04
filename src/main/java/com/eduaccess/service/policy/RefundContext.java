package com.eduaccess.service.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable input record passed to a {@link CancellationPolicy}.
 * <p>
 * Carries everything a policy needs to compute a refund:
 * <ul>
 *   <li>{@code movieAmount} — the original ticket price (pre-refund).</li>
 *   <li>{@code foodAmount} — the running total of all food orders attached
 *       to the booking that are still cancellable (PENDING / PREPARING).</li>
 *   <li>{@code vipPackageAmount} — the virtual VIP membership add-on fee
 *       included in the booking when {@code vipCustomer} is true.</li>
 *   <li>{@code includeMovie / includeFood / includeVipPackage} — which line
 *       items the administrator ticked in the Refund Items selector.</li>
 *   <li>{@code scope} — full vs partial refund decision.</li>
 *   <li>{@code vipCustomer} — whether the booking is flagged as VIP, used
 *       by {@link EmergencyPolicy} to decide the bonus voucher.</li>
 *   <li>{@code screeningDate} — the date of the screening, used to determine
 *       if this is a same-day or past screening (0% refund for Standard/VIP).</li>
 * </ul>
 * <p>
 * 传递给 {@link CancellationPolicy} 的不可变输入记录。
 * <p>
 * 携带策略计算退款所需的一切信息：
 * <ul>
 *   <li>{@code movieAmount} — 原始电影票价格（退款前）。</li>
 *   <li>{@code foodAmount} — 附加到预订的所有食品订单的总计,仍然是可取消的(PENDING/PREPARING)。</li>
 *   <li>{@code vipPackageAmount} — 当 {@code vipCustomer} 为 true 时包含在预订中的虚拟VIP会员附加费。</li>
 *   <li>{@code includeMovie / includeFood / includeVipPackage} — 管理员在退款项目选择器中勾选的项目。</li>
 *   <li>{@code scope} — 全额或部分退款决定。</li>
 *   <li>{@code vipCustomer} — 预订是否标记为VIP,由 {@link EmergencyPolicy} 用于决定奖励优惠券。</li>
 *   <li>{@code screeningDate} — 放映日期,用于确定是否为同日或过去的场次(标准/VIP策略0%退款)。</li>
 * </ul>
 * The record is immutable so policies can be freely cached, parallelised
 * and unit-tested without fear of side-effects.
 * <p>
 * 该记录是不可变的,因此策略可以自由缓存、并行化和单元测试,而无需担心副作用。
 */
public record RefundContext(
        BigDecimal movieAmount,
        BigDecimal foodAmount,
        BigDecimal vipPackageAmount,
        boolean includeMovie,
        boolean includeFood,
        boolean includeVipPackage,
        RefundScope scope,
        boolean vipCustomer,
        LocalDate screeningDate
) {

    /** Defensive copy so {@code null} amounts never crash arithmetic. */
    public RefundContext {
        movieAmount = movieAmount == null ? BigDecimal.ZERO : movieAmount;
        foodAmount = foodAmount == null ? BigDecimal.ZERO : foodAmount;
        vipPackageAmount = vipPackageAmount == null ? BigDecimal.ZERO : vipPackageAmount;
        scope = scope == null ? RefundScope.PARTIAL : scope;
    }
}
