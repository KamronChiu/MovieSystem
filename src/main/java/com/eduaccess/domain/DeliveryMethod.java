package com.eduaccess.domain;

public enum DeliveryMethod {
    COUNTER_PICKUP("Collect at counter"),
    DELIVER_TO_SEAT("Deliver to selected seat");

    private final String label;

    DeliveryMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
