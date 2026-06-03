package com.eduaccess.service.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CancellationPolicyFactory}.
 * <p>
 * Verifies the Strategy-pattern registry: all three policies are registered,
 * lookup works correctly, and unknown types throw.
 */
class CancellationPolicyFactoryTest {

    private final CancellationPolicyFactory factory = new CancellationPolicyFactory(
            List.of(new StandardPolicy(), new VIPPolicy(), new EmergencyPolicy())
    );

    @Test
    @DisplayName("policyFor_standard_returnsStandardPolicy")
    void policyFor_standard_returnsStandardPolicy() {
        CancellationPolicy policy = factory.policyFor(PolicyType.STANDARD);
        assertThat(policy).isInstanceOf(StandardPolicy.class);
        assertThat(policy.getType()).isEqualTo(PolicyType.STANDARD);
    }

    @Test
    @DisplayName("policyFor_vip_returnsVIPPolicy")
    void policyFor_vip_returnsVIPPolicy() {
        CancellationPolicy policy = factory.policyFor(PolicyType.VIP);
        assertThat(policy).isInstanceOf(VIPPolicy.class);
    }

    @Test
    @DisplayName("policyFor_emergency_returnsEmergencyPolicy")
    void policyFor_emergency_returnsEmergencyPolicy() {
        CancellationPolicy policy = factory.policyFor(PolicyType.EMERGENCY);
        assertThat(policy).isInstanceOf(EmergencyPolicy.class);
    }

    @Test
    @DisplayName("policyFor_null_throwsNullPointerException")
    void policyFor_null_throwsNullPointerException() {
        assertThatThrownBy(() -> factory.policyFor(null))
                .isInstanceOf(RuntimeException.class);
    }
}
