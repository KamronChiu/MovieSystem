package com.eduaccess.service;

import com.eduaccess.domain.Screening;
import com.eduaccess.domain.Seat;
import com.eduaccess.domain.SeatType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Collection;

@Service
public class PricingService {

    private static final BigDecimal UPPER_GALLERY_EXTRA = new BigDecimal("2.00");

    public BigDecimal calculateTicketPrice(Screening screening, Seat seat) {
        String city = screening.getScreen().getCinema().getCity();
        LocalTime startTime = screening.getStartTime();

        BigDecimal basePrice = getLowerHallPrice(city, startTime);

        if (seat.getSeatType() == SeatType.UPPER_GALLERY) {
            return basePrice.add(UPPER_GALLERY_EXTRA);
        }

        return basePrice;
    }

    public BigDecimal calculateTotalPrice(Screening screening, Collection<Seat> seats) {
        BigDecimal total = BigDecimal.ZERO;

        for (Seat seat : seats) {
            total = total.add(calculateTicketPrice(screening, seat));
        }

        return total;
    }

    private BigDecimal getLowerHallPrice(String city, LocalTime startTime) {
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