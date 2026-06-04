package com.eduaccess.service.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT_005 — Unit tests for {@link EmergencyPolicy}.
 * <p>
 * Emergency policy honours the refund scope:
 *   FULL    → 100% refund on every selected line item.
 *   PARTIAL → 50%  refund on every selected line item.
 * VIP customers also receive a 50%-off voucher.
 */
class EmergencyPolicyTest {

    private final EmergencyPolicy policy = new EmergencyPolicy();

    @Test
    @DisplayName("calculate_fullScope_returnsFullRefund")
    void calculate_fullScope_returnsFullRefund() {
        // Emergency policy works for any date (including same-day)
        // 紧急策略适用于任何日期（包括同日）
        RefundContext ctx = new RefundContext(
                new BigDecimal("20.00"),
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                true,
                true,
                false,
                RefundScope.FULL,
                false,   // not VIP → no voucher
                LocalDate.now()  // same-day (emergency allows refund)
        );

        PolicyRefundResult result = policy.calculate(ctx);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("20.00");
        assertThat(result.getFoodRefund()).isEqualByComparingTo("5.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("25.00");
        assertThat(result.getVoucher()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("calculate_vipFullScope_issuesVoucher")
    void calculate_vipFullScope_issuesVoucher() {
        RefundContext ctx = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                false,
                RefundScope.FULL,
                true,    // VIP → voucher should be issued
                LocalDate.now().plusDays(1)
        );

        PolicyRefundResult result = policy.calculate(ctx);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("20.00");
        // Voucher face value = 50% of ticket price = 10.00
        assertThat(result.getVoucher()).isEqualByComparingTo("10.00");
        assertThat(result.getPolicyType()).isEqualTo(PolicyType.EMERGENCY);
    }

    @Test
    @DisplayName("calculate_partialScope_returnsHalfOnEveryLine")
    void calculate_partialScope_returnsHalfOnEveryLine() {
        RefundContext ctx = new RefundContext(
                new BigDecimal("20.00"),
                new BigDecimal("8.00"),
                BigDecimal.ZERO,
                true,
                true,
                false,
                RefundScope.PARTIAL,
                false,
                LocalDate.now().plusDays(3)
        );

        PolicyRefundResult result = policy.calculate(ctx);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("10.00");
        assertThat(result.getFoodRefund()).isEqualByComparingTo("4.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("14.00");
    }
}
