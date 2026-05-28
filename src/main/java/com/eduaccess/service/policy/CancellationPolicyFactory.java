package com.eduaccess.service.policy;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy registry / factory that maps {@link PolicyType} to a concrete
 * {@link CancellationPolicy} bean.
 * <p>
 * Spring injects <em>every</em> {@link CancellationPolicy} implementation
 * into the constructor; the factory groups them into an {@link EnumMap}
 * keyed by {@link CancellationPolicy#getType()}. This means:
 * <ul>
 *   <li>The UI / service layer never references a concrete policy class.</li>
 *   <li>Adding a new policy is a pure additive change (a new
 *       {@code @Component} that implements the interface) — the factory
 *       auto-picks it up at startup without any modification.</li>
 *   <li>Lookup is O(1) via the EnumMap.</li>
 * </ul>
 * No if/switch chain anywhere — polymorphism through {@link CancellationPolicy}
 * does all the work.
 */
@Component
public class CancellationPolicyFactory {

    private final Map<PolicyType, CancellationPolicy> registry;

    public CancellationPolicyFactory(List<CancellationPolicy> policies) {
        EnumMap<PolicyType, CancellationPolicy> map = new EnumMap<>(PolicyType.class);
        for (CancellationPolicy policy : policies) {
            map.put(policy.getType(), policy);
        }
        this.registry = Map.copyOf(map);
    }

    /**
     * Returns the policy bean registered for the given type.
     *
     * @param type the requested policy type (must not be null)
     * @return the matching policy bean, never null
     * @throws IllegalArgumentException if no policy is registered for the type
     */
    public CancellationPolicy policyFor(PolicyType type) {
        CancellationPolicy policy = registry.get(type);
        if (policy == null) {
            throw new IllegalArgumentException(
                    "No CancellationPolicy bean registered for " + type);
        }
        return policy;
    }
}
