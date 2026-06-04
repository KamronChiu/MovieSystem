package com.eduaccess.service;

import com.eduaccess.domain.*;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.FoodOrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT_023 — Integration test: Booking with food concessions.
 * Verifies that after creating a booking, a food order can be placed
 * against it and persists with PENDING status and correct total.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking-food-it;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "vaadin.launch-browser=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class BookingServiceIT {

    @Autowired private BookingService bookingService;
    @Autowired private FoodOrderService foodOrderService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private FoodOrderRepository foodOrderRepository;
    @Autowired private EntityManager entityManager;

    private Screening futureScreening;
    private Seat seatA1;
    private FoodItem popcorn;

    @BeforeEach
    void setUp() {
        Cinema cinema = new Cinema("Food IT Cinema", "London", "10 Food St");
        entityManager.persist(cinema);

        Screen screen = new Screen(cinema, 1, 60, HallType.REGULAR);
        entityManager.persist(screen);

        seatA1 = new Seat(screen, "A1", SeatType.STANDARD);
        entityManager.persist(seatA1);

        Film film = new Film("Food IT Film", "Great", "Actor", "Action", "12A", 110);
        entityManager.persist(film);

        futureScreening = new Screening(film, screen, LocalDate.now().plusDays(1), LocalTime.of(20, 0));
        entityManager.persist(futureScreening);

        popcorn = new FoodItem("Large Popcorn", FoodCategory.POPCORN, new BigDecimal("6.50"), null, true);
        entityManager.persist(popcorn);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("IT_023 createBooking_withFood_createsFoodOrder")
    void createBooking_withFood_createsFoodOrder() {
        // 1) Create booking
        Booking booking = bookingService.createBooking(
                futureScreening.getId(),
                List.of(seatA1.getId()),
                "Food Customer",
                "food@test.com"
        );
        assertThat(booking).isNotNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // 2) Create food order attached to the booking
        FoodOrder foodOrder = foodOrderService.createFoodOrder(
                booking.getId(),
                Map.of(popcorn.getId(), 2),
                DeliveryMethod.COUNTER_PICKUP
        );

        // 3) Verify food order persisted correctly
        assertThat(foodOrder).isNotNull();
        assertThat(foodOrder.getStatus()).isEqualTo(FoodOrderStatus.PENDING);
        // 2 x £6.50 = £13.00
        assertThat(foodOrder.getTotalCost()).isEqualByComparingTo("13.00");
        assertThat(foodOrder.getItems()).hasSize(1);
        assertThat(foodOrder.getItems().get(0).getQuantity()).isEqualTo(2);

        // 4) Verify linkage in database
        List<FoodOrder> orders = foodOrderService.findOrdersForBooking(booking.getId());
        assertThat(orders).hasSize(1);
    }
}
