package com.eduaccess.domain;

public enum FoodOrderStatus {
    PENDING("Pending"),
    PREPARING("Preparing"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    private final String label;

    FoodOrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
