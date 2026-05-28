package com.eduaccess.service.policy;

/**
 * Refund scope chosen by the administrator on the Refund Decision Panel.
 * <p>
 * Distinguishes the two business scenarios required by Task 9:
 * <ul>
 *   <li>{@link #FULL} — total refund. May only be paired with
 *       {@link PolicyType#EMERGENCY} because only the emergency policy
 *       allows a same-day full refund.</li>
 *   <li>{@link #PARTIAL} — proportional refund. Allowed with any non-emergency
 *       policy ({@link PolicyType#STANDARD} or {@link PolicyType#VIP}).</li>
 * </ul>
 * The UI layer enforces the pairing rule; the policy classes themselves
 * remain agnostic of the scope and simply use the configured rates.
 */
public enum RefundScope {

    FULL("Full Refund"),
    PARTIAL("Partial Refund");

    private final String displayName;

    RefundScope(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
