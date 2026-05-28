package com.eduaccess.service.compensation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Immutable value object representing a single compensation item handed out
 * to a VIP customer alongside the refund.
 * <p>
 * The Compensation Card on the Refund Decision Panel renders one of these
 * per row, and the Details dialog projects every field directly:
 * <ul>
 *   <li>{@link #getName()}        — voucher name shown on the card.</li>
 *   <li>{@link #getDescription()} — full marketing description shown in the dialog.</li>
 *   <li>{@link #getValue()}       — face value (£). Zero is allowed for non-monetary perks.</li>
 *   <li>{@link #getExpiryDate()}  — fixed at issue-date + 1 year.</li>
 *   <li>{@link #getType()}        — {@link CompensationItemType} for grouping/icons.</li>
 * </ul>
 * Front-end demo only — items are never persisted (per Task 10 brief).
 */
public final class CompensationItem {

    /** Voucher validity = 1 year from issue date (TASK 10 brief). */
    private static final int VOUCHER_VALIDITY_YEARS = 1;

    private final CompensationItemType type;
    private final String name;
    private final String description;
    private final BigDecimal value;
    private final LocalDate issueDate;
    private final LocalDate expiryDate;

    public CompensationItem(CompensationItemType type, BigDecimal value) {
        this(type, type.getDisplayName(), type.getDescription(), value, LocalDate.now());
    }

    public CompensationItem(CompensationItemType type,
                            String name,
                            String description,
                            BigDecimal value,
                            LocalDate issueDate) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.value = (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP);
        this.issueDate = issueDate == null ? LocalDate.now() : issueDate;
        this.expiryDate = this.issueDate.plusYears(VOUCHER_VALIDITY_YEARS);
    }

    public CompensationItemType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getValue() {
        return value;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}
