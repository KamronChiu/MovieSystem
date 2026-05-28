package com.eduaccess.service.policy;

/**
 * Identifier for the three refund policies an administrator may pick from
 * inside the Refund Decision Panel (Task 9).
 * <p>
 * The enum is the only way the UI references a policy — the concrete
 * {@link CancellationPolicy} implementations are looked up by
 * {@link CancellationPolicyFactory#policyFor(PolicyType)}.
 * <p>
 * Adding a new policy (e.g. {@code STUDENT}, {@code HOLIDAY}, {@code IMAX})
 * only requires:
 * <ol>
 *   <li>declaring a new constant here,</li>
 *   <li>creating a new {@link CancellationPolicy} bean, and</li>
 *   <li>nothing else — the factory auto-discovers Spring-managed policies.</li>
 * </ol>
 * Existing policy classes do <strong>not</strong> need to be modified
 * (Open/Closed Principle).
 */
public enum PolicyType {

    STANDARD("Standard Policy",
            "Movie tickets refunded at 50%, food and add-ons refunded in full."),
    VIP("VIP Policy",
            "Movie tickets refunded at 70% (+20% over standard), food refunded in full."),
    EMERGENCY("Emergency Policy",
            "Same-day full refund on every item; VIP customers also receive a 50%-off voucher.");

    private final String displayName;
    private final String description;

    PolicyType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
