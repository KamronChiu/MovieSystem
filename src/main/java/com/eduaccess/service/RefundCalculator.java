package com.eduaccess.service;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.RefundSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Calculates refund amounts for booking cancellations.
 * <p>
 * Business rules:
 * <ul>
 *   <li>Standard cancellation: 50% refund</li>
 *   <li>Same-day cancellation: £0 refund (but flow can still show "Refunded")</li>
 *   <li>VIP customer: +20% bonus on the refund amount</li>
 *   <li>Weekend (Saturday/Sunday) screening: −10% on the refund amount</li>
 * </ul>
 * <p>
 * All calculation logic lives in this service; the UI must never
 * perform arithmetic directly. Future adjustment factors can be
 * added by creating new {@link RefundAdjustment} implementations
 * and registering them in the adjustment chain.
 */
@Service
public class RefundCalculator {

    // ── Base rates ────────────────────────────────────────────────────────
    private static final BigDecimal STANDARD_REFUND_RATE = new BigDecimal("0.50");
    private static final BigDecimal SAME_DAY_REFUND_RATE = BigDecimal.ZERO;
    private static final BigDecimal VIP_BONUS_RATE = new BigDecimal("0.20");
    private static final BigDecimal WEEKEND_PENALTY_RATE = new BigDecimal("0.10");

    private static final int MONEY_SCALE = 2;

    /**
     * Calculates the full refund summary for the given booking.
     * <p>
     * The method applies the base refund rate first (50% for normal
     * cancellations, 0% for same-day cancellations), then adjusts
     * the resulting refund amount by VIP bonus (+20%) and weekend
     * penalty (−10%) if applicable.
     *
     * @param booking the booking whose refund is being calculated
     * @return a {@link RefundSummary} containing all amounts and
     *         human-readable adjustment descriptions
     */
    public RefundSummary calculate(Booking booking) {
        BigDecimal originalAmount = booking.getTotalCost();
        RefundSummary.Builder builder = new RefundSummary.Builder()
                .originalAmount(originalAmount);

        // ── Step 1: Determine base refund rate ────────────────────────────
        boolean sameDay = isSameDayCancellation(booking);
        BigDecimal baseRate = sameDay ? SAME_DAY_REFUND_RATE : STANDARD_REFUND_RATE;

        if (sameDay) {
            builder.addAdjustment("Same-day cancellation: 0% refund");
        } else {
            builder.addAdjustment("Standard cancellation: 50% refund");
        }

        BigDecimal baseRefund = originalAmount.multiply(baseRate)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // ── Step 2: VIP bonus (+20% of base refund) ───────────────────────
        BigDecimal refundAfterVip = baseRefund;
        if (booking.isVip()) {
            BigDecimal vipBonus = baseRefund.multiply(VIP_BONUS_RATE)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            refundAfterVip = baseRefund.add(vipBonus);
            builder.addAdjustment("VIP bonus: +20% (" + formatRate(VIP_BONUS_RATE) + ") → +" + vipBonus);
        }

        // ── Step 3: Weekend penalty (−10% of refund after VIP) ───────────
        BigDecimal finalRefund = refundAfterVip;
        if (isWeekendScreening(booking)) {
            BigDecimal weekendPenalty = refundAfterVip.multiply(WEEKEND_PENALTY_RATE)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            finalRefund = refundAfterVip.subtract(weekendPenalty);
            builder.addAdjustment("Weekend screening: −10% (" + formatRate(WEEKEND_PENALTY_RATE) + ") → −" + weekendPenalty);
        }

        // Ensure refund is never negative
        finalRefund = finalRefund.max(BigDecimal.ZERO);

        BigDecimal feeAmount = originalAmount.subtract(finalRefund)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        builder.refundAmount(finalRefund);
        builder.feeAmount(feeAmount);

        return builder.build();
    }

    // ── Helper: same-day check ────────────────────────────────────────────

    /**
     * Returns true if the screening date is today or earlier, meaning
     * the cancellation is happening on the same day as (or after) the show.
     *
     * @param booking the booking to check
     * @return true if this is a same-day or past-date cancellation
     */
    boolean isSameDayCancellation(Booking booking) {
        LocalDate today = LocalDate.now();
        LocalDate screeningDate = booking.getScreening().getScreeningDate();
        return !screeningDate.isAfter(today);
    }

    // ── Helper: weekend check ────────────────────────────────────────────

    /**
     * Returns true if the screening falls on a Saturday or Sunday.
     *
     * @param booking the booking to check
     * @return true if the screening date is a weekend day
     */
    boolean isWeekendScreening(Booking booking) {
        DayOfWeek day = booking.getScreening().getScreeningDate().getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    // ── Formatting ────────────────────────────────────────────────────────

    private String formatRate(BigDecimal rate) {
        return rate.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP) + "%";
    }
}