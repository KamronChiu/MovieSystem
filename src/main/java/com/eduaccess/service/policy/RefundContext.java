package com.eduaccess.service.policy;

import java.math.BigDecimal;

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
 * </ul>
 * The record is immutable so policies can be freely cached, parallelised
 * and unit-tested without fear of side-effects.
 */
public record RefundContext(
        BigDecimal movieAmount,
        BigDecimal foodAmount,
        BigDecimal vipPackageAmount,
        boolean includeMovie,
        boolean includeFood,
        boolean includeVipPackage,
        RefundScope scope,
        boolean vipCustomer
) {

    /** Defensive copy so {@code null} amounts never crash arithmetic. */
    public RefundContext {
        movieAmount = movieAmount == null ? BigDecimal.ZERO : movieAmount;
        foodAmount = foodAmount == null ? BigDecimal.ZERO : foodAmount;
        vipPackageAmount = vipPackageAmount == null ? BigDecimal.ZERO : vipPackageAmount;
        scope = scope == null ? RefundScope.PARTIAL : scope;
    }
}
