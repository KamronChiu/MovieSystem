package com.eduaccess.service.email;

import com.eduaccess.domain.Booking;
import com.eduaccess.service.policy.PolicyRefundResult;
import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UT_009 / UT_010 — Unit tests for {@link EmailReceiptService}.
 * <p>
 * Verifies the de-duplication fix introduced in this codebase:
 * each single-cancellation flow must leave exactly ONE entry in the
 * EmailLogService — not two — and that entry must carry the receipt.
 */
class EmailReceiptServiceTest {

    private EmailLogService emailLog;
    private EmailReceiptService service;

    @BeforeEach
    void setUp() {
        emailLog = new EmailLogService();
        service = new EmailReceiptService(emailLog);
    }

    @Test
    @DisplayName("buildSingleEmail_writesOneLogEntry")
    void buildSingleEmail_writesOneLogEntry() {
        Booking booking = mockBooking();
        PolicyRefundResult result = standardResult();

        service.buildSingleEmail(booking, PolicyType.STANDARD, result, null,
                LocalDateTime.now());

        assertThat(emailLog.count()).isEqualTo(1L);
        assertThat(emailLog.findAll().get(0).getReceipt()).isNull();
    }

    @Test
    @DisplayName("buildSingleReceipt_replacesPreviousEntry")
    void buildSingleReceipt_replacesPreviousEntry() {
        Booking booking = mockBooking();
        PolicyRefundResult result = standardResult();

        // Step 1 — build email (1 log entry, receipt=null)
        service.buildSingleEmail(booking, PolicyType.STANDARD, result, null,
                LocalDateTime.now());
        assertThat(emailLog.count()).isEqualTo(1L);

        // Step 2 — build receipt → must REPLACE the previous entry,
        // not append a second one (DEF_001 regression guard).
        service.buildSingleReceipt(booking, PolicyType.STANDARD, RefundScope.PARTIAL,
                result, null, "operator", LocalDateTime.now());

        assertThat(emailLog.count())
                .as("Single flow must leave exactly ONE log entry after attach")
                .isEqualTo(1L);
        assertThat(emailLog.findAll().get(0).getReceipt())
                .as("Surviving entry must carry the receipt")
                .isNotNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Booking mockBooking() {
        Booking booking = mock(Booking.class);
        when(booking.getBookingReference()).thenReturn("HCBS-T-EMAIL");
        when(booking.getCustomerName()).thenReturn("Alice");
        when(booking.getCustomerEmail()).thenReturn("alice@test.com");
        when(booking.getTotalCost()).thenReturn(new BigDecimal("20.00"));
        // getScreening() returns null — the email service tolerates a
        // missing screening (extractCinemaName / extractFilmTitle return null).
        return booking;
    }

    private PolicyRefundResult standardResult() {
        return new PolicyRefundResult.Builder()
                .policyType(PolicyType.STANDARD)
                .scope(RefundScope.PARTIAL)
                .movieRefund(new BigDecimal("10.00"))
                .build();
    }
}
