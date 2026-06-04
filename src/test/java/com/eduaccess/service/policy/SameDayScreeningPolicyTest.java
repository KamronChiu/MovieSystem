package com.eduaccess.service.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for same-day/past screening refund logic across all policies.
 * 测试所有策略的同日/已放映场次退款逻辑。
 * <p>
 * Business rules:
 * 业务规则：
 * <ul>
 *   <li>Standard/VIP Policy: Same-day or past screening → £0 movie refund
 *       (标准/VIP策略：同日或已放映场次 → 电影票0元退款)</li>
 *   <li>Emergency Policy: Same-day or past screening → Full refund allowed
 *       (紧急策略：同日或已放映场次 → 允许全额退款)</li>
 *   <li>Future screening: Standard 50%, VIP 70%
 *       (未来场次：标准50%，VIP 70%)</li>
 * </ul>
 */
class SameDayScreeningPolicyTest {

    private final StandardPolicy standardPolicy = new StandardPolicy();
    private final VIPPolicy vipPolicy = new VIPPolicy();
    private final EmergencyPolicy emergencyPolicy = new EmergencyPolicy();

    // ── Standard Policy Tests / 标准策略测试 ─────────────────────────────

    @Test
    @DisplayName("StandardPolicy_SameDayScreening_ZeroRefund")
    void standardPolicy_sameDayScreening_zeroRefund() {
        // Same-day screening should result in £0 movie refund
        // 同日场次应导致电影票0元退款
        RefundContext context = createContext(
                new BigDecimal("20.00"),  // movie amount
                BigDecimal.ZERO,          // food amount
                false,                    // include movie
                false,                    // include food
                false,                    // include VIP package
                RefundScope.PARTIAL,
                false,                    // VIP customer
                LocalDate.now()           // screening date = today
        );
        context = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,                     // include movie
                false,
                false,
                RefundScope.PARTIAL,
                false,
                LocalDate.now()
        );

        PolicyRefundResult result = standardPolicy.calculate(context);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("0.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("0.00");
        assertThat(result.getBreakdownLines())
                .anyMatch(line -> line.contains("Same-day/past screening"));
    }

    @Test
    @DisplayName("StandardPolicy_PastScreening_ZeroRefund")
    void standardPolicy_pastScreening_zeroRefund() {
        // Past screening (yesterday) should also result in £0 movie refund
        // 过去的场次（昨天）也应导致电影票0元退款
        RefundContext context = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                false,
                RefundScope.PARTIAL,
                false,
                LocalDate.now().minusDays(1)  // yesterday
        );

        PolicyRefundResult result = standardPolicy.calculate(context);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("0.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("StandardPolicy_FutureScreening_50PercentRefund")
    void standardPolicy_futureScreening_fiftyPercentRefund() {
        // Future screening should get standard 50% refund
        // 未来场次应获得标准50%退款
        RefundContext context = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                false,
                RefundScope.PARTIAL,
                false,
                LocalDate.now().plusDays(3)  // 3 days in future
        );

        PolicyRefundResult result = standardPolicy.calculate(context);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("10.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("10.00");
    }

    // ── VIP Policy Tests / VIP策略测试 ────────────────────────────────────

    @Test
    @DisplayName("VIPPolicy_SameDayScreening_ZeroRefund")
    void vipPolicy_sameDayScreening_zeroRefund() {
        // Same-day screening should result in £0 movie refund even for VIP
        // 同日场次即使是VIP也应导致电影票0元退款
        RefundContext context = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                false,
                RefundScope.PARTIAL,
                true,                     // VIP customer
                LocalDate.now()
        );

        PolicyRefundResult result = vipPolicy.calculate(context);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("0.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("VIPPolicy_FutureScreening_70PercentRefund")
    void vipPolicy_futureScreening_seventyPercentRefund() {
        // Future screening should get VIP 70% refund
        // 未来场次应获得VIP 70%退款
        RefundContext context = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                false,
                RefundScope.PARTIAL,
                true,
                LocalDate.now().plusDays(5)
        );

        PolicyRefundResult result = vipPolicy.calculate(context);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("14.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("14.00");
    }

    // ── Emergency Policy Tests / 紧急策略测试 ─────────────────────────────

    @Test
    @DisplayName("EmergencyPolicy_SameDayScreening_FullRefund")
    void emergencyPolicy_sameDayScreening_fullRefund() {
        // Emergency policy allows full refund even for same-day screening
        // 紧急策略允许同日场次全额退款
        RefundContext context = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                false,
                RefundScope.FULL,         // Full refund scope
                false,
                LocalDate.now()
        );

        PolicyRefundResult result = emergencyPolicy.calculate(context);

        // Emergency policy bypasses same-day restriction
        // 紧急策略绕过同日限制
        assertThat(result.getMovieRefund()).isEqualByComparingTo("20.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("EmergencyPolicy_PastScreening_FullRefund")
    void emergencyPolicy_pastScreening_fullRefund() {
        // Emergency policy allows full refund even for past screening
        // 紧急策略允许已放映场次全额退款
        RefundContext context = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                false,
                RefundScope.FULL,
                false,
                LocalDate.now().minusDays(2)  // 2 days ago
        );

        PolicyRefundResult result = emergencyPolicy.calculate(context);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("20.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("EmergencyPolicy_SameDay_PartialRefund_50Percent")
    void emergencyPolicy_sameDay_partialRefund_fiftyPercent() {
        // Emergency policy with PARTIAL scope should give 50% even for same-day
        // 紧急策略使用部分退款范围，即使是同日也应给50%
        RefundContext context = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                false,
                RefundScope.PARTIAL,      // Partial refund scope
                false,
                LocalDate.now()
        );

        PolicyRefundResult result = emergencyPolicy.calculate(context);

        assertThat(result.getMovieRefund()).isEqualByComparingTo("10.00");
        assertThat(result.getFinalRefund()).isEqualByComparingTo("10.00");
    }

    // ── Helper / 辅助方法 ─────────────────────────────────────────────────

    private RefundContext createContext(
            BigDecimal movieAmount,
            BigDecimal foodAmount,
            boolean includeMovie,
            boolean includeFood,
            boolean includeVipPackage,
            RefundScope scope,
            boolean vipCustomer,
            LocalDate screeningDate
    ) {
        return new RefundContext(
                movieAmount,
                foodAmount,
                BigDecimal.ZERO,
                includeMovie,
                includeFood,
                includeVipPackage,
                scope,
                vipCustomer,
                screeningDate
        );
    }
}
