package com.eduaccess.service.compensation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable bundle of {@link CompensationItem}s produced by the
 * {@link VIPBenefitService} for a single refund decision.
 * <p>
 * Mirrors the Compensation Card on the Refund Pending step:
 * <ul>
 *   <li>{@link #getItems()}      — every voucher / coupon / bonus row to render.</li>
 *   <li>{@link #getTotalValue()} — sum of all face values (used in the dialog footer).</li>
 *   <li>{@link #isEmpty()}       — convenience for hiding the card when no benefits apply.</li>
 *   <li>{@link #getHeadline()}   — short marketing headline (e.g. "VIP EMERGENCY BENEFITS").</li>
 * </ul>
 * Use {@link Builder} to construct instances; the package itself is unmodifiable.
 */
public final class CompensationPackage {

    /** Empty package shorthand — used for non-VIP customers. */
    public static final CompensationPackage EMPTY = new Builder()
            .headline("No Compensation")
            .build();

    private final String headline;
    private final List<CompensationItem> items;
    private final BigDecimal totalValue;

    private CompensationPackage(Builder b) {
        this.headline = b.headline == null ? "" : b.headline;
        this.items = Collections.unmodifiableList(new ArrayList<>(b.items));
        BigDecimal sum = BigDecimal.ZERO;
        for (CompensationItem item : this.items) {
            sum = sum.add(item.getValue());
        }
        this.totalValue = sum.setScale(2, RoundingMode.HALF_UP);
    }

    public String getHeadline() {
        return headline;
    }

    public List<CompensationItem> getItems() {
        return items;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public static class Builder {
        private String headline;
        private final List<CompensationItem> items = new ArrayList<>();

        public Builder headline(String headline) {
            this.headline = headline;
            return this;
        }

        public Builder add(CompensationItem item) {
            if (item != null) {
                this.items.add(item);
            }
            return this;
        }

        public CompensationPackage build() {
            return new CompensationPackage(this);
        }
    }
}
