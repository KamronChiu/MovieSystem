package com.eduaccess.domain;

public enum HallType {
    REGULAR("Regular"),
    IMAX("IMAX"),
    PREMIUM("Premium");

    private final String label;

    HallType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}