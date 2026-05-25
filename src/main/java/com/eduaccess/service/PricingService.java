package com.eduaccess.service;

import com.eduaccess.domain.HallType;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.domain.Seat;
import com.eduaccess.domain.SeatType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Collection;

@Service
public class PricingService {

    private static final BigDecimal IMAX_SURCHARGE = new BigDecimal("3.00");
    private static final BigDecimal PREMIUM_SURCHARGE = new BigDecimal("5.00");

    private static final BigDecimal PREMIUM_SEAT_EXTRA = new BigDecimal("2.50");
    private static final BigDecimal CENTER_SEAT_EXTRA = new BigDecimal("4.00");

    private static final BigDecimal THREE_D_SURCHARGE = new BigDecimal("2.00");

    public BigDecimal calculateTicketPrice(Screening screening, Seat seat) {
        String city = screening.getScreen().getCinema().getCity();
        LocalTime startTime = screening.getStartTime();
        HallType hallType = screening.getScreen().getHallType();
        ScreeningType screeningType = screening.getScreeningType();

        BigDecimal basePrice = getBasePrice(city, startTime);
        BigDecimal hallSurcharge = getHallSurcharge(hallType);
        BigDecimal seatSurcharge = getSeatSurcharge(seat.getSeatType());
        BigDecimal threeDSurcharge = get3DSurcharge(screeningType);

        return basePrice.add(hallSurcharge).add(seatSurcharge).add(threeDSurcharge);
    }

    public BigDecimal getHallSurcharge(HallType hallType) {
        return switch (hallType) {
            case IMAX -> IMAX_SURCHARGE;
            case PREMIUM -> PREMIUM_SURCHARGE;
            case REGULAR -> BigDecimal.ZERO;
        };
    }

    public BigDecimal getSeatSurcharge(SeatType seatType) {
        return switch (seatType) {
            case PREMIUM -> PREMIUM_SEAT_EXTRA;
            case CENTER -> CENTER_SEAT_EXTRA;
            case STANDARD -> BigDecimal.ZERO;
        };
    }

    public BigDecimal get3DSurcharge(ScreeningType screeningType) {
        if (screeningType != null && screeningType.is3D()) {
            return THREE_D_SURCHARGE;
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal calculateTotalPrice(Screening screening, Collection<Seat> seats) {
        BigDecimal total = BigDecimal.ZERO;

        for (Seat seat : seats) {
            total = total.add(calculateTicketPrice(screening, seat));
        }

        return total;
    }

    private BigDecimal getBasePrice(String city, LocalTime startTime) {
        ShowPeriod period = getShowPeriod(startTime);

        return switch (city.toLowerCase()) {
            case "birmingham" -> switch (period) {
                case MORNING -> new BigDecimal("5.00");
                case AFTERNOON -> new BigDecimal("6.00");
                case EVENING -> new BigDecimal("7.00");
            };
            case "bristol" -> switch (period) {
                case MORNING -> new BigDecimal("6.00");
                case AFTERNOON -> new BigDecimal("7.00");
                case EVENING -> new BigDecimal("8.00");
            };
            case "cardiff" -> switch (period) {
                case MORNING -> new BigDecimal("5.00");
                case AFTERNOON -> new BigDecimal("6.00");
                case EVENING -> new BigDecimal("7.00");
            };
            case "london" -> switch (period) {
                case MORNING -> new BigDecimal("10.00");
                case AFTERNOON -> new BigDecimal("11.00");
                case EVENING -> new BigDecimal("12.00");
            };
            default -> throw new IllegalArgumentException("Unsupported city for pricing: " + city);
        };
    }

    private ShowPeriod getShowPeriod(LocalTime startTime) {
        if (startTime.isBefore(LocalTime.NOON)) {
            return ShowPeriod.MORNING;
        }

        if (startTime.isBefore(LocalTime.of(17, 0))) {
            return ShowPeriod.AFTERNOON;
        }

        return ShowPeriod.EVENING;
    }

    private enum ShowPeriod {
        MORNING,
        AFTERNOON,
        EVENING
    }
}