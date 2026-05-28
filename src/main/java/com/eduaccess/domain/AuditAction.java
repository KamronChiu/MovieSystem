package com.eduaccess.domain;

public enum AuditAction {
    BOOKING_CREATED("Booking created"),
    BOOKING_CANCELLED("Booking cancelled"),
    FOOD_ORDER_CREATED("Food order created"),
    FOOD_ORDER_UPDATED("Food order updated"),
    CINEMA_CREATED("Cinema created"),
    SCREEN_CREATED("Screen created"),
    SCREENING_CREATED("Screening created"),
    SCREENING_UPDATED("Screening updated"),
    SCREENING_DELETED("Screening deleted"),
    FEEDBACK_CREATED("Dashboard feedback created"),

    // Compatibility with the older cancellation/audit code that may still exist in the project.
    CANCEL_BOOKING("Cancel booking"),
    ADVANCE_STATUS("Advance status"),
    UPDATE_REASON("Update cancellation reason"),
    UPDATE_VIP("Update VIP flag"),
    SYSTEM_EVENT("System event");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AuditAction fromCode(String value) {
        if (value == null || value.isBlank()) {
            return SYSTEM_EVENT;
        }
        String normalized = value.trim().toUpperCase();
        for (AuditAction action : values()) {
            if (action.name().equals(normalized)) {
                return action;
            }
        }
        return SYSTEM_EVENT;
    }
}
