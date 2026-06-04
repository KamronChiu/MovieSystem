package com.eduaccess.service;

import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.HallType;
import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.domain.Seat;
import com.eduaccess.domain.SeatType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT_011 — Unit tests for {@link PricingService}.
 * <p>
 * Verifies the city-based base price plus all surcharges.
 */
class PricingServiceTest {

    private final PricingService pricing = new PricingService();

    @Test
    @DisplayName("priceFor_3DScreening_addsSurcharge")
    void priceFor_3DScreening_addsSurcharge() {
        BigDecimal threeD = pricing.get3DSurcharge(ScreeningType.REGULAR_3D);
        BigDecimal twoD = pricing.get3DSurcharge(ScreeningType.REGULAR_2D);

        assertThat(threeD).isEqualByComparingTo("2.00");
        assertThat(twoD).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("hallSurcharge_imaxAndPremiumAndRegular")
    void hallSurcharge_imaxAndPremiumAndRegular() {
        assertThat(pricing.getHallSurcharge(HallType.IMAX)).isEqualByComparingTo("3.00");
        assertThat(pricing.getHallSurcharge(HallType.PREMIUM)).isEqualByComparingTo("5.00");
        assertThat(pricing.getHallSurcharge(HallType.REGULAR)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("seatSurcharge_byType")
    void seatSurcharge_byType() {
        assertThat(pricing.getSeatSurcharge(SeatType.STANDARD)).isEqualByComparingTo("0.00");
        assertThat(pricing.getSeatSurcharge(SeatType.PREMIUM)).isEqualByComparingTo("2.50");
        assertThat(pricing.getSeatSurcharge(SeatType.CENTER)).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("calculateTicketPrice_londonEvening3DPremium_summedCorrectly")
    void calculateTicketPrice_londonEvening3DPremium_summedCorrectly() {
        // London base evening (>=17:00) = 12.00
        // PREMIUM hall = +5.00, PREMIUM seat = +2.50, 3D = +2.00
        // → 12 + 5 + 2.50 + 2.00 = 21.50
        Cinema cinema = new Cinema("HCBS Bank", "London", "1 Bank St");
        Screen screen = new Screen(cinema, 1, 50, HallType.PREMIUM);
        Screening screening = new ScreeningStub(screen, LocalTime.of(19, 0),
                ScreeningType.REGULAR_3D);
        Seat seat = new Seat(screen, "A1", SeatType.PREMIUM);

        BigDecimal price = pricing.calculateTicketPrice(screening, seat);

        assertThat(price).isEqualByComparingTo("21.50");
    }

    /** Lightweight Screening test double — avoids needing a Film instance. */
    private static class ScreeningStub extends Screening {
        private final Screen screen;
        private final LocalTime startTime;
        private final ScreeningType type;
        ScreeningStub(Screen screen, LocalTime startTime, ScreeningType type) {
            this.screen = screen;
            this.startTime = startTime;
            this.type = type;
        }
        @Override public Screen getScreen() { return screen; }
        @Override public LocalTime getStartTime() { return startTime; }
        @Override public ScreeningType getScreeningType() { return type; }
        @Override public LocalDate getScreeningDate() { return LocalDate.now().plusDays(2); }
    }
}
