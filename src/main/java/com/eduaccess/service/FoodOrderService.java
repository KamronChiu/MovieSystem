package com.eduaccess.service;

import com.eduaccess.domain.AuditAction;
import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.DeliveryMethod;
import com.eduaccess.domain.FoodItem;
import com.eduaccess.domain.FoodOrder;
import com.eduaccess.domain.FoodOrderItem;
import com.eduaccess.domain.FoodOrderStatus;
import com.eduaccess.exception.ResourceNotFoundException;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.BookingSeatRepository;
import com.eduaccess.repository.FoodItemRepository;
import com.eduaccess.repository.FoodOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FoodOrderService {

    private final FoodItemRepository foodItemRepository;
    private final FoodOrderRepository foodOrderRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final AuditLogService auditLogService;

    public FoodOrderService(
            FoodItemRepository foodItemRepository,
            FoodOrderRepository foodOrderRepository,
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            AuditLogService auditLogService
    ) {
        this.foodItemRepository = foodItemRepository;
        this.foodOrderRepository = foodOrderRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<FoodItem> findActiveFoodItems() {
        return foodItemRepository.findByActiveTrueOrderByCategoryAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<FoodOrder> findAllOrders() {
        return foodOrderRepository.findAllByOrderByOrderTimeDesc();
    }

    @Transactional(readOnly = true)
    public List<FoodOrder> findOpenOrders() {
        return foodOrderRepository.findByStatusInOrderByOrderTimeAsc(
                List.of(FoodOrderStatus.PENDING, FoodOrderStatus.PREPARING)
        );
    }

    @Transactional(readOnly = true)
    public List<FoodOrder> findOrdersForBooking(Long bookingId) {
        return foodOrderRepository.findByBooking_Id(bookingId);
    }

    @Transactional(readOnly = true)
    public String findSeatNumbersForBooking(Long bookingId) {
        List<String> seatNumbers = bookingSeatRepository.findSeatNumbersByBookingId(bookingId);
        return seatNumbers.isEmpty() ? "-" : String.join(", ", seatNumbers);
    }

    @Transactional
    public FoodOrder createFoodOrder(
            Long bookingId,
            Map<Long, Integer> itemQuantities,
            DeliveryMethod deliveryMethod
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot add food to a cancelled booking.");
        }

        Map<Long, Integer> cleanedQuantities = cleanQuantities(itemQuantities);
        if (cleanedQuantities.isEmpty()) {
            throw new IllegalArgumentException("No food items were selected.");
        }

        FoodOrder order = new FoodOrder(booking, deliveryMethod);
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> entry : cleanedQuantities.entrySet()) {
            FoodItem foodItem = foodItemRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Food item not found."));

            if (!foodItem.isActive()) {
                throw new IllegalArgumentException(foodItem.getName() + " is not currently available.");
            }

            int quantity = entry.getValue();
            BigDecimal unitPrice = foodItem.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            FoodOrderItem orderItem = new FoodOrderItem(foodItem, quantity, unitPrice);
            orderItem.setLineTotal(lineTotal);
            order.addItem(orderItem);

            total = total.add(lineTotal);
        }

        order.setTotalCost(total);
        FoodOrder savedOrder = foodOrderRepository.save(order);

        auditLogService.record(
                AuditAction.FOOD_ORDER_CREATED,
                "FoodOrder",
                savedOrder.getId(),
                booking.getBookingReference(),
                booking.getScreening().getFilm().getTitle(),
                booking.getScreening().getScreen().getCinema().getName(),
                savedOrder.getTotalCost(),
                "Food order created for " + booking.getBookingReference(),
                "Delivery: " + savedOrder.getDeliveryMethod().getLabel()
                        + "; Items: " + foodOrderItemsText(savedOrder)
        );

        return savedOrder;
    }

    @Transactional
    public void cancelPendingFoodOrdersForBooking(Long bookingId) {
        List<FoodOrder> orders = foodOrderRepository.findByBooking_Id(bookingId);

        for (FoodOrder order : orders) {
            if (order.getStatus() == FoodOrderStatus.PENDING || order.getStatus() == FoodOrderStatus.PREPARING) {
                order.setStatus(FoodOrderStatus.CANCELLED);
                auditLogService.record(
                        AuditAction.FOOD_ORDER_UPDATED,
                        "FoodOrder",
                        order.getId(),
                        order.getBooking().getBookingReference(),
                        order.getBooking().getScreening().getFilm().getTitle(),
                        order.getBooking().getScreening().getScreen().getCinema().getName(),
                        order.getTotalCost(),
                        "Food order cancelled after booking cancellation",
                        foodOrderItemsText(order)
                );
            }
        }
    }

    @Transactional
    public FoodOrder markPreparing(Long orderId) {
        FoodOrder order = findOrderOrThrow(orderId);

        if (order.getStatus() == FoodOrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled food orders cannot be prepared.");
        }
        if (order.getStatus() == FoodOrderStatus.DELIVERED) {
            throw new IllegalStateException("Delivered food orders cannot be changed back to preparing.");
        }

        order.setStatus(FoodOrderStatus.PREPARING);
        auditFoodOrderStatus(order, "Food order marked preparing");
        return order;
    }

    @Transactional
    public FoodOrder markDelivered(Long orderId) {
        FoodOrder order = findOrderOrThrow(orderId);

        if (order.getStatus() == FoodOrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled food orders cannot be delivered.");
        }

        order.setStatus(FoodOrderStatus.DELIVERED);
        auditFoodOrderStatus(order, "Food order marked delivered");
        return order;
    }

    @Transactional
    public FoodOrder cancelOrder(Long orderId) {
        FoodOrder order = findOrderOrThrow(orderId);

        if (order.getStatus() == FoodOrderStatus.DELIVERED) {
            throw new IllegalStateException("Delivered food orders cannot be cancelled.");
        }

        order.setStatus(FoodOrderStatus.CANCELLED);
        auditFoodOrderStatus(order, "Food order cancelled");
        return order;
    }

    private FoodOrder findOrderOrThrow(Long orderId) {
        return foodOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Food order not found."));
    }

    private void auditFoodOrderStatus(FoodOrder order, String summary) {
        auditLogService.record(
                AuditAction.FOOD_ORDER_UPDATED,
                "FoodOrder",
                order.getId(),
                order.getBooking().getBookingReference(),
                order.getBooking().getScreening().getFilm().getTitle(),
                order.getBooking().getScreening().getScreen().getCinema().getName(),
                order.getTotalCost(),
                summary,
                "Status: " + order.getStatus() + "; " + foodOrderItemsText(order)
        );
    }

    private String foodOrderItemsText(FoodOrder order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return "No items";
        }

        return order.getItems().stream()
                .map(item -> item.getFoodItem().getName() + " x" + item.getQuantity())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private Map<Long, Integer> cleanQuantities(Map<Long, Integer> itemQuantities) {
        if (itemQuantities == null || itemQuantities.isEmpty()) {
            return Map.of();
        }

        return itemQuantities.entrySet()
                .stream()
                .filter(entry -> entry.getKey() != null)
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        Integer::sum,
                        java.util.LinkedHashMap::new
                ));
    }
}
