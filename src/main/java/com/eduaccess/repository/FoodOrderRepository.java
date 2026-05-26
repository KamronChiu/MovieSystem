package com.eduaccess.repository;

import com.eduaccess.domain.FoodOrder;
import com.eduaccess.domain.FoodOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FoodOrderRepository extends JpaRepository<FoodOrder, Long> {

    @EntityGraph(attributePaths = {
            "booking",
            "booking.screening",
            "booking.screening.film",
            "booking.screening.screen",
            "booking.screening.screen.cinema",
            "items",
            "items.foodItem"
    })
    List<FoodOrder> findAllByOrderByOrderTimeDesc();

    @EntityGraph(attributePaths = {
            "booking",
            "booking.screening",
            "booking.screening.film",
            "booking.screening.screen",
            "booking.screening.screen.cinema",
            "items",
            "items.foodItem"
    })
    List<FoodOrder> findByStatusInOrderByOrderTimeAsc(Collection<FoodOrderStatus> statuses);

    @EntityGraph(attributePaths = {
            "booking",
            "booking.screening",
            "booking.screening.film",
            "booking.screening.screen",
            "booking.screening.screen.cinema",
            "items",
            "items.foodItem"
    })
    List<FoodOrder> findByBooking_Id(Long bookingId);
}
