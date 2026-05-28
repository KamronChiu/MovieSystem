package com.eduaccess.service.policy;

/**
 * Strategy interface for refund policies (Task 9).
 * <p>
 * Each concrete policy encapsulates one set of refund rules:
 * <ul>
 *   <li>{@link StandardPolicy}  — 50% on tickets, 100% on food.</li>
 *   <li>{@link VIPPolicy}       — 70% on tickets, 100% on food.</li>
 *   <li>{@link EmergencyPolicy} — 100% on every line item plus a
 *       50%-off voucher for VIP customers.</li>
 * </ul>
 * The Refund Decision Panel never reads or branches on the policy class;
 * it asks the {@link CancellationPolicyFactory} for the policy mapped to
 * the {@link PolicyType} the administrator selected, then calls
 * {@link #calculate(RefundContext)}. Polymorphism replaces if/else chains.
 * <p>
 * Adding a future policy ({@code StudentPolicy}, {@code HolidayPolicy},
 * {@code IMAXPolicy}, …) is a pure additive change: declare the constant
 * in {@link PolicyType}, write a new {@code @Component} that implements
 * this interface, done. Existing implementations are not edited
 * (Open/Closed Principle).
 */
public interface CancellationPolicy {

    /**
     * Identifies which policy this is. The factory groups policies by
     * this key when assembling the lookup table.
     *
     * @return non-null policy type
     */
    PolicyType getType();

    /**
     * Computes the refund breakdown for the given context.
     *
     * @param context immutable input — booking amounts and selected items
     * @return immutable result containing every line item shown in the UI
     */
    PolicyRefundResult calculate(RefundContext context);
}
