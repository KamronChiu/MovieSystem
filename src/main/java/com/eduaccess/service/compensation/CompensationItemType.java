package com.eduaccess.service.compensation;

/**
 * Catalogue of every benefit a VIP customer may receive on top of a refund.
 * <p>
 * Each constant identifies a specific compensation product. Adding a new
 * benefit (e.g. {@link #FREE_POPCORN}, {@link #BIRTHDAY_VOUCHER},
 * {@link #PREMIUM_SEAT_UPGRADE}) only requires:
 * <ol>
 *   <li>declaring a constant here, and</li>
 *   <li>extending {@link com.eduaccess.service.compensation.VIPBenefitService}
 *       to wire the new item into the appropriate
 *       {@link com.eduaccess.service.policy.PolicyType} branch.</li>
 * </ol>
 * No change is needed in any policy class, the cancellation service or the UI.
 */
public enum CompensationItemType {

    /** Half-price voucher — Emergency + VIP only. Optional. */
    HALF_PRICE_VOUCHER("Half-price Voucher",
            "Entitles the holder to 50% off any single ticket within the validity period."),
    /** Free drink coupon — Emergency + VIP only. Optional. */
    FREE_DRINK_COUPON("Free Drink Coupon",
            "Redeemable for one complimentary soft drink at any HCBS cinema."),
    /** Bonus refund — Standard/VIP policies, displayed as a label only (already baked into VIPPolicy 70%). */
    BONUS_REFUND("Bonus Refund",
            "+20% refund on the movie ticket compared to the standard rate (already applied)."),

    // ── Reserved future-extension slots (TASK 10 brief) ──────────────────
    /** Reserved — issue a free popcorn coupon. */
    FREE_POPCORN("Free Popcorn",
            "Redeemable for one regular popcorn at the concession counter."),
    /** Reserved — birthday voucher. */
    BIRTHDAY_VOUCHER("Birthday Voucher",
            "Special-occasion voucher gifted to VIP customers on their birthday month."),
    /** Reserved — free upgrade to a premium seat. */
    PREMIUM_SEAT_UPGRADE("Premium Seat Upgrade",
            "Free upgrade from a Standard seat to a Premium seat on the next visit.");

    private final String displayName;
    private final String description;

    CompensationItemType(String displayName, String description) {
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
