package com.eduaccess.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Enumerates all possible statuses for a {@link Booking}.
 * <p>
 * Each constant overrides {@link #getAllowedTransitions()} to declare its own
 * valid target statuses. State-transition rules therefore live in one single
 * place; future statuses can be added by simply declaring a new enum constant.
 * <p>
 * Current linear state machine:
 * <pre>
 *   CONFIRMED ──→ CANCELLED ──→ REFUND_PENDING ──→ REFUNDED
 * </pre>
 * Only {@code REFUNDED} is a terminal state.
 */
public enum BookingStatus {

    CONFIRMED("Confirmed") {
        @Override
        public Set<BookingStatus> getAllowedTransitions() {
            return EnumSet.of(CANCELLED);
        }
        @Override
        public boolean isCancellable() {
            return true;
        }
        @Override
        public String getBadgeBackground() {
            return "#0072ce";
        }
        @Override
        public String getBadgeTextColor() {
            return "#ffffff";
        }
    },

    CANCELLED("Cancelled") {
        @Override
        public Set<BookingStatus> getAllowedTransitions() {
            return EnumSet.of(REFUND_PENDING);
        }
        @Override
        public String getBadgeBackground() {
            return "#64748b";
        }
        @Override
        public String getBadgeTextColor() {
            return "#ffffff";
        }
    },

    REFUND_PENDING("Refund Pending") {
        @Override
        public Set<BookingStatus> getAllowedTransitions() {
            return EnumSet.of(REFUNDED);
        }
        @Override
        public String getBadgeBackground() {
            return "#f59e0b";
        }
        @Override
        public String getBadgeTextColor() {
            return "#ffffff";
        }
    },

    REFUNDED("Refunded") {
        @Override
        public Set<BookingStatus> getAllowedTransitions() {
            return EnumSet.noneOf(BookingStatus.class);
        }
        @Override
        public String getBadgeBackground() {
            return "#10b981";
        }
        @Override
        public String getBadgeTextColor() {
            return "#ffffff";
        }
    };

    /** Human-readable label for UI rendering. */
    private final String displayName;

    BookingStatus(String displayName) {
        this.displayName = displayName;
    }

    // ── Abstract / overridable behaviour ──────────────────────────────────

    /**
     * Returns the set of statuses this status is allowed to transition into.
     * Each constant overrides this method to declare its own valid targets.
     *
     * @return unmodifiable view of allowed next statuses
     */
    public abstract Set<BookingStatus> getAllowedTransitions();

    /**
     * Returns the background colour for a status badge in the UI.
     *
     * @return CSS colour value
     */
    public abstract String getBadgeBackground();

    /**
     * Returns the text colour for a status badge in the UI.
     *
     * @return CSS colour value
     */
    public abstract String getBadgeTextColor();

    // ── State-transition helpers ──────────────────────────────────────────

    /**
     * Returns {@code true} if this status may transition to {@code target}.
     *
     * @param target the desired next status
     * @return whether the transition is valid
     */
    public boolean canTransitionTo(BookingStatus target) {
        return getAllowedTransitions().contains(target);
    }

    /**
     * A booking is cancellable only when its current status is
     * {@link #CONFIRMED}. Other statuses override this to return
     * {@code false} by default.
     *
     * @return {@code true} if the booking can be cancelled right now
     */
    public boolean isCancellable() {
        return false;
    }

    /**
     * Terminal states have no outgoing transitions; once a booking
     * reaches one of these it will never change again.
     *
     * @return {@code true} if this status is terminal
     */
    public boolean isTerminal() {
        return getAllowedTransitions().isEmpty();
    }

    /**
     * Returns the next status in the refund flow, or {@code null} if this
     * is a terminal status with no outgoing transitions.
     *
     * @return the single allowed next status, or null if terminal
     */
    public BookingStatus nextInFlow() {
        Set<BookingStatus> transitions = getAllowedTransitions();
        return transitions.isEmpty() ? null : transitions.iterator().next();
    }

    // ── Display helper ────────────────────────────────────────────────────

    /**
     * Returns a human-readable label suitable for UI display.
     *
     * @return display name (never null)
     */
    public String getDisplayName() {
        return displayName;
    }
}
