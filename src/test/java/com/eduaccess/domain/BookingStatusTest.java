package com.eduaccess.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UT_006 / UT_007 — Unit tests for {@link BookingStatus} state-machine
 * and the {@link Booking#transitionTo(BookingStatus)} guard.
 */
class BookingStatusTest {

    @Test
    @DisplayName("canTransitionTo_invalidPath_returnsFalse")
    void canTransitionTo_invalidPath_returnsFalse() {
        // CONFIRMED can ONLY go to CANCELLED — any other target must be false.
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.REFUNDED))
                .isFalse();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.REFUND_PENDING))
                .isFalse();

        // REFUNDED is terminal.
        assertThat(BookingStatus.REFUNDED.canTransitionTo(BookingStatus.CONFIRMED))
                .isFalse();
        assertThat(BookingStatus.REFUNDED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("canTransitionTo_validPath_returnsTrue")
    void canTransitionTo_validPath_returnsTrue() {
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED))
                .isTrue();
        assertThat(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.REFUND_PENDING))
                .isTrue();
        assertThat(BookingStatus.REFUND_PENDING.canTransitionTo(BookingStatus.REFUNDED))
                .isTrue();
    }

    @Test
    @DisplayName("transitionTo_invalidPath_throws")
    void transitionTo_invalidPath_throws() {
        Booking booking = new Booking("HCBS-T-1", null, "alice", "alice@test.com");
        // CONFIRMED → REFUNDED is illegal; must throw IllegalStateException.

        assertThatThrownBy(() -> booking.transitionTo(BookingStatus.REFUNDED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("isCancellable_onlyConfirmed_returnsTrue")
    void isCancellable_onlyConfirmed_returnsTrue() {
        assertThat(BookingStatus.CONFIRMED.isCancellable()).isTrue();
        assertThat(BookingStatus.CANCELLED.isCancellable()).isFalse();
        assertThat(BookingStatus.REFUND_PENDING.isCancellable()).isFalse();
        assertThat(BookingStatus.REFUNDED.isCancellable()).isFalse();
    }

    @Test
    @DisplayName("nextInFlow_followsLinearStateMachine")
    void nextInFlow_followsLinearStateMachine() {
        assertThat(BookingStatus.CONFIRMED.nextInFlow()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(BookingStatus.CANCELLED.nextInFlow()).isEqualTo(BookingStatus.REFUND_PENDING);
        assertThat(BookingStatus.REFUND_PENDING.nextInFlow()).isEqualTo(BookingStatus.REFUNDED);
        assertThat(BookingStatus.REFUNDED.nextInFlow()).isNull();
    }
}
