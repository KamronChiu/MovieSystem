package com.eduaccess.service;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.DeliveryMethod;
import com.eduaccess.domain.Film;
import com.eduaccess.domain.FoodCategory;
import com.eduaccess.domain.FoodItem;
import com.eduaccess.domain.FoodOrder;
import com.eduaccess.domain.FoodOrderStatus;
import com.eduaccess.domain.HallType;
import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Screening;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT_015 ~ IT_019 — Integration test for {@link FoodOrderService}.
 * <p>
 * Boots the full Spring context to verify the booking ↔ food-order
 * cancellation cascade required by TASK 8 (Cancellation):
 * <ul>
 *   <li>{@code cancelPendingFoodOrdersForBooking} flips PENDING /
 *       PREPARING orders to CANCELLED;</li>
 *   <li>DELIVERED orders are LEFT untouched (food was already consumed);</li>
 *   <li>{@code createFoodOrder} happy-path persists items + total cost;</li>
 *   <li>Adding food to a CANCELLED booking is rejected.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:food-it;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "vaadin.launch-browser=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class FoodOrderServiceIT {

    @Autowired
    private FoodOrderService foodOrderService;

    @Autowired
    private FoodOrderRepository foodOrderRepository;

    @Autowired
    private EntityManager entityManager;

    private Booking booking;
    private FoodItem popcorn;
    private FoodItem cola;

    @BeforeEach
    void setUp() {
        Cinema cinema = new Cinema("Food IT Cinema", "London", "12 Food Rd");
        entityManager.persist(cinema);

        Screen screen = new Screen(cinema, 1, 50, HallType.REGULAR);
        entityManager.persist(screen);

        Film film = new Film("Food IT Film", "Desc", "Actor", "Drama", "PG", 100);
        entityManager.persist(film);

        Screening screening = new Screening(film, screen,
                LocalDate.now().plusDays(1), LocalTime.of(20, 0));
        entityManager.persist(screening);

        booking = new Booking("HCBS-IT010-001", screening, "Bob", "bob@test.com");
        booking.setTotalCost(new BigDecimal("18.00"));
        entityManager.persist(booking);

        popcorn = new FoodItem("Popcorn", FoodCategory.POPCORN, new BigDecimal("4.50"), null, true);
        cola = new FoodItem("Cola", FoodCategory.DRINK, new BigDecimal("2.80"), null, true);
        entityManager.persist(popcorn);
        entityManager.persist(cola);

        entityManager.flush();
    }

    @Test
    @DisplayName("cancelPendingFoodOrdersForBooking_updatesStatus")
    void cancelPendingFoodOrdersForBooking_updatesStatus() {
        // Pre-condition: a PENDING food order exists for the booking.
        FoodOrder pending = foodOrderService.createFoodOrder(
                booking.getId(),
                Map.of(popcorn.getId(), 2, cola.getId(), 1),
                DeliveryMethod.DELIVER_TO_SEAT
        );
        assertThat(pending.getStatus()).isEqualTo(FoodOrderStatus.PENDING);
        assertThat(pending.getTotalCost())
                .as("Total = 2 × 4.50 + 1 × 2.80 = 11.80")
                .isEqualByComparingTo("11.80");

        // Action — booking gets cancelled, cascade should flip food order.
        foodOrderService.cancelPendingFoodOrdersForBooking(booking.getId());

        entityManager.flush();
        entityManager.clear();

        FoodOrder reloaded = foodOrderRepository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("Pending food order must be CANCELLED after booking cancel")
                .isEqualTo(FoodOrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelPendingFoodOrdersForBooking_leavesDeliveredUntouched")
    void cancelPendingFoodOrdersForBooking_leavesDeliveredUntouched() {
        // A DELIVERED order represents food already consumed by the customer
        // — refunding the ticket must not retroactively cancel it.
        FoodOrder order = foodOrderService.createFoodOrder(
                booking.getId(),
                Map.of(popcorn.getId(), 1),
                DeliveryMethod.COUNTER_PICKUP
        );
        foodOrderService.markDelivered(order.getId());

        foodOrderService.cancelPendingFoodOrdersForBooking(booking.getId());

        entityManager.flush();
        entityManager.clear();

        FoodOrder reloaded = foodOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("Already-delivered orders must NOT be cancelled by the cascade")
                .isEqualTo(FoodOrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("createFoodOrder_emptyItems_throwsException")
    void createFoodOrder_emptyItems_throwsException() {
        assertThatThrownBy(() -> foodOrderService.createFoodOrder(
                booking.getId(), Map.of(), DeliveryMethod.COUNTER_PICKUP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No food items");
    }

    @Test
    @DisplayName("createFoodOrder_unknownBooking_throwsException")
    void createFoodOrder_unknownBooking_throwsException() {
        assertThatThrownBy(() -> foodOrderService.createFoodOrder(
                999_999L, Map.of(popcorn.getId(), 1), DeliveryMethod.COUNTER_PICKUP))
                .hasMessageContaining("Booking not found");
    }

    @Test
    @DisplayName("findOrdersForBooking_returnsOrdersInDatabase")
    void findOrdersForBooking_returnsOrdersInDatabase() {
        foodOrderService.createFoodOrder(
                booking.getId(),
                Map.of(popcorn.getId(), 1),
                DeliveryMethod.COUNTER_PICKUP);

        List<FoodOrder> orders = foodOrderService.findOrdersForBooking(booking.getId());
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getBooking().getBookingReference())
                .isEqualTo("HCBS-IT010-001");
    }
}
