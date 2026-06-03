package com.eduaccess.service.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT_012 — Unit tests for {@link RefundContext} record.
 * <p>
 * Verifies the defensive-null defaulting in the compact constructor.
 */
class RefundContextTest {

    @Test
    @DisplayName("nullAmounts_areCoercedToZero")
    void nullAmounts_areCoercedToZero() {
        RefundContext ctx = new RefundContext(
                null, null, null,
                true, true, true,
                null, false
        );

        assertThat(ctx.movieAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ctx.foodAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ctx.vipPackageAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ctx.scope()).isEqualTo(RefundScope.PARTIAL);
    }

    @Test
    @DisplayName("vipPackageAmount_whenNotVip_isZero")
    void vipPackageAmount_whenNotVip_isZero() {
        // The caller (CancellationService.buildPolicyContext) sets
        // vipPackageAmount=0 when booking.isVip()==false; we re-assert that
        // the record itself faithfully retains zero.
        RefundContext ctx = new RefundContext(
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true, false, false,
                RefundScope.PARTIAL, false
        );

        assertThat(ctx.vipPackageAmount()).isEqualByComparingTo("0.00");
        assertThat(ctx.vipCustomer()).isFalse();
    }
}
