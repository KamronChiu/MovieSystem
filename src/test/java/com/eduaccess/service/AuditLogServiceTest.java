package com.eduaccess.service;

import com.eduaccess.domain.AuditAction;
import com.eduaccess.domain.AuditLog;
import com.eduaccess.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UT_025 — Unit tests for {@link AuditLogService}.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private LoginService loginService;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @DisplayName("recordAction_nullActor_usesSystemFallback")
    void recordAction_nullActor_usesSystemFallback() {
        // When loginService.getCurrentUser() throws (no Vaadin session),
        // the audit entry must default to "system" actor, not DB null.
        when(loginService.getCurrentUser()).thenThrow(new RuntimeException("No VaadinSession"));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog result = auditLogService.record(
                AuditAction.BOOKING_CREATED,
                "Booking",
                1L,
                "HCBS-UT025-001",
                "Film",
                "Cinema",
                new BigDecimal("20.00"),
                "Test summary",
                "Test details"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        // operator should default to "system" instead of DB null
        assertThat(saved.getActorUsername()).isEqualTo("system");
        assertThat(saved.getActorName()).isEqualTo("System");
        assertThat(saved.getActorRole()).isEqualTo("SYSTEM");
        assertThat(saved.getAction()).isEqualTo(AuditAction.BOOKING_CREATED);
    }
}
