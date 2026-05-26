package com.eduaccess.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "food_orders")
public class FoodOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 40)
    private DeliveryMethod deliveryMethod = DeliveryMethod.COUNTER_PICKUP;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodOrderStatus status = FoodOrderStatus.PENDING;

    @Column(name = "order_time", nullable = false)
    private LocalDateTime orderTime = LocalDateTime.now();

    @Column(name = "total_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @OneToMany(mappedBy = "foodOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FoodOrderItem> items = new ArrayList<>();

    protected FoodOrder() {
    }

    public FoodOrder(Booking booking, DeliveryMethod deliveryMethod) {
        this.booking = booking;
        this.deliveryMethod = deliveryMethod == null ? DeliveryMethod.COUNTER_PICKUP : deliveryMethod;
        this.status = FoodOrderStatus.PENDING;
        this.orderTime = LocalDateTime.now();
        this.totalCost = BigDecimal.ZERO;
    }

    public void addItem(FoodOrderItem item) {
        items.add(item);
        item.setFoodOrder(this);
    }

    public void removeItem(FoodOrderItem item) {
        items.remove(item);
        item.setFoodOrder(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public DeliveryMethod getDeliveryMethod() {
        return deliveryMethod;
    }

    public void setDeliveryMethod(DeliveryMethod deliveryMethod) {
        this.deliveryMethod = deliveryMethod == null ? DeliveryMethod.COUNTER_PICKUP : deliveryMethod;
    }

    public FoodOrderStatus getStatus() {
        return status;
    }

    public void setStatus(FoodOrderStatus status) {
        this.status = status == null ? FoodOrderStatus.PENDING : status;
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(LocalDateTime orderTime) {
        this.orderTime = orderTime;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost == null ? BigDecimal.ZERO : totalCost;
    }

    public List<FoodOrderItem> getItems() {
        return items;
    }

    public void setItems(List<FoodOrderItem> items) {
        this.items = items;
    }
}
