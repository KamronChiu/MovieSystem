package com.eduaccess.domain;

public enum FoodCategory {
    POPCORN("Popcorn"),
    FRIES("Fries"),
    DRINK("Drink"),
    COMBO("Combo");

    private final String label;

    FoodCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
