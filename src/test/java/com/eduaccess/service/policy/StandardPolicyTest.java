package com.eduaccess.service.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT_001 / UT_002 — Unit tests for {@link StandardPolicy}.
 * <p>
 * Pure logic tests — no Spring context required.
 */
class StandardPolicyTest {

    private final StandardPolicy policy = new StandardPolicy();

    @Test
    @DisplayName("calculate_movieOnly_returnsHalfRefund")
    void calculate_movieOnly_returnsHalfRefund() {
        RefundContext ctx = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,   // include movie
                false,
                false,
                RefundScope.PARTIAL,
                false
        );

        PolicyRefundResult result = policy.calculate(ctx);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("10.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("10.00");
        assertThat(result.getVoucher()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("calculate_movieAndFood_returnsHalfTicketAndFullFood")
    void calculate_movieAndFood_returnsHalfTicketAndFullFood() {
        RefundContext ctx = new RefundContext(
                new BigDecimal("30.00"),
                new BigDecimal("12.00"),
                BigDecimal.ZERO,
                true,
                true,
                false,
                RefundScope.PARTIAL,
                false
        );

        PolicyRefundResult result = policy.calculate(ctx);

        // 30 * 0.5 = 15.00 ticket; 12 * 1.0 = 12.00 food => total 27.00
        assertThat(result.getMovieRefund()).isEqualByComparingTo("15.00");
        assertThat(result.getFoodRefund()).isEqualByComparingTo("12.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("27.00");
        assertThat(result.getPolicyType()).isEqualTo(PolicyType.STANDARD);
    }

    @Test
    @DisplayName("getType_returnsStandard")
    void getType_returnsStandard() {
        assertThat(policy.getType()).isEqualTo(PolicyType.STANDARD);
    }
}
