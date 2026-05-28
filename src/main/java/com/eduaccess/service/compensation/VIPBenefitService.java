package com.eduaccess.service.compensation;

import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Generates the {@link CompensationPackage} shown on the Compensation Card
 * of the Refund Decision Panel.
 * <p>
 * The service is intentionally thin: it owns <strong>only</strong> the rule
 * of <em>"who gets what compensation"</em>. The monetary refund itself is
 * still computed by the {@link com.eduaccess.service.policy.CancellationPolicy}
 * strategy chain, so this class can be evolved or replaced without touching
 * any policy class.
 * <p>
 * <b>Compensation matrix (TASK 10):</b>
 * <table>
 *   <tr><th>Customer</th><th>Policy</th><th>Compensation</th></tr>
 *   <tr><td>non-VIP</td><td>any</td><td>none</td></tr>
 *   <tr><td>VIP</td><td>STANDARD / VIP</td><td>Bonus Refund label (+20% baked into VIP Policy 70%)</td></tr>
 *   <tr><td>VIP</td><td>EMERGENCY</td><td>optional Half-price Voucher and/or Free Drink Coupon</td></tr>
 * </table>
 * <p>
 * <b>Future-extension contract:</b> adding {@link CompensationItemType#FREE_POPCORN},
 * {@link CompensationItemType#BIRTHDAY_VOUCHER} or
 * {@link CompensationItemType#PREMIUM_SEAT_UPGRADE} only requires extending
 * one of the matrix branches below — every existing policy class, the UI's
 * radio/checkbox wiring and the persistence layer are untouched.
 */
@Service
public class VIPBenefitService {

    /** Half-price voucher face value = 50% of the ticket price. */
    private static final BigDecimal HALF_PRICE_VOUCHER_RATE = new BigDecimal("0.50");
    /** Free drink coupon face value (£). Demo only — not persisted. */
    private static final BigDecimal FREE_DRINK_COUPON_VALUE = new BigDecimal("4.50");
    /** Bonus-refund label "value" — informational only (the +20% is already in VIPPolicy). */
    private static final BigDecimal BONUS_REFUND_RATE = new BigDecimal("0.20");

    /**
     * Builds the compensation package for the supplied refund decision.
     *
     * @param policyType                 selected refund policy
     * @param context                    refund context (carries movieAmount + vipCustomer flag)
     * @param includeHalfPriceVoucher    Emergency-only opt-in checkbox
     * @param includeFreeDrinkCoupon     Emergency-only opt-in checkbox
     * @return immutable {@link CompensationPackage} ready to be rendered;
     *         never {@code null} (use {@link CompensationPackage#EMPTY} when
     *         no benefits apply)
     */
    public CompensationPackage build(PolicyType policyType,
                                     RefundContext context,
                                     boolean includeHalfPriceVoucher,
                                     boolean includeFreeDrinkCoupon) {
        if (context == null || policyType == null || !context.vipCustomer()) {
            return CompensationPackage.EMPTY;
        }

        return switch (policyType) {
            case EMERGENCY -> buildEmergencyPackage(context,
                    includeHalfPriceVoucher, includeFreeDrinkCoupon);
            case STANDARD, VIP -> buildOrdinaryPackage(context, policyType);
        };
    }

    /**
     * Emergency + VIP — issue the two opt-in vouchers chosen on the panel.
     */
    private CompensationPackage buildEmergencyPackage(RefundContext context,
                                                      boolean includeHalfPriceVoucher,
                                                      boolean includeFreeDrinkCoupon) {
        CompensationPackage.Builder b = new CompensationPackage.Builder()
                .headline("VIP EMERGENCY BENEFITS");

        if (includeHalfPriceVoucher && context.movieAmount().signum() > 0) {
            BigDecimal value = context.movieAmount().multiply(HALF_PRICE_VOUCHER_RATE);
            b.add(new CompensationItem(CompensationItemType.HALF_PRICE_VOUCHER, value));
        }
        if (includeFreeDrinkCoupon) {
            b.add(new CompensationItem(CompensationItemType.FREE_DRINK_COUPON,
                    FREE_DRINK_COUPON_VALUE));
        }
        return b.build();
    }

    /**
     * Standard / VIP policies — issue the Bonus Refund informational label.
     * The +20% bonus is already baked into {@link PolicyType#VIP}'s 70%
     * vs Standard's 50%, so this item is shown for transparency only.
     */
    private CompensationPackage buildOrdinaryPackage(RefundContext context,
                                                     PolicyType policyType) {
        CompensationPackage.Builder b = new CompensationPackage.Builder()
                .headline("VIP BONUS");

        BigDecimal bonusValue = policyType == PolicyType.VIP
                ? context.movieAmount().multiply(BONUS_REFUND_RATE)
                : BigDecimal.ZERO;
        b.add(new CompensationItem(
                CompensationItemType.BONUS_REFUND,
                CompensationItemType.BONUS_REFUND.getDisplayName(),
                policyType == PolicyType.VIP
                        ? "+20% extra ticket refund vs Standard rate (already applied to the Final Refund)."
                        : "Switch to VIP Policy to unlock the +20% bonus refund.",
                bonusValue,
                java.time.LocalDate.now()));
        return b.build();
    }
}
