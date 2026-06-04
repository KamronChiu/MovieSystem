package com.eduaccess.service.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT_003 / UT_004 — Unit tests for {@link VIPPolicy}.
 */
class VIPPolicyTest {

    private final VIPPolicy policy = new VIPPolicy();

    @Test
    @DisplayName("calculate_movieOnly_returns70Percent")
    void calculate_movieOnly_returns70Percent() {
        // Use future date to ensure 70% refund applies
        // 使用未来日期确保应用70%退款
        RefundContext ctx = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                false,
                RefundScope.PARTIAL,
                true,
                LocalDate.now().plusDays(2)
        );

        PolicyRefundResult result = policy.calculate(ctx);

        // 20 * 0.7 = 14.00
        assertThat(result.getMovieRefund()).isEqualByComparingTo("14.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("14.00");
    }

    @Test
    @DisplayName("calculate_includesFood_returns100PercentFood")
    void calculate_includesFood_returns100PercentFood() {
        // Use future date to ensure 70% refund applies
        // 使用未来日期确保应用70%退款
        RefundContext ctx = new RefundContext(
                new BigDecimal("20.00"),
                new BigDecimal("8.50"),
                new BigDecimal("10.00"),
                true,
                true,
                true,
                RefundScope.PARTIAL,
                true,
                LocalDate.now().plusDays(4)
        );

        PolicyRefundResult result = policy.calculate(ctx);

        // 20*0.7 + 8.50*1 + 10*1 = 14 + 8.50 + 10 = 32.50
        assertThat(result.getMovieRefund()).isEqualByComparingTo("14.00");
        assertThat(result.getFoodRefund()).isEqualByComparingTo("8.50");
        assertThat(result.getVipPackageRefund()).isEqualByComparingTo("10.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("32.50");
        assertThat(result.getPolicyType()).isEqualTo(PolicyType.VIP);
    }
}
