package com.eduaccess.domain;

public enum ScreeningType {
    REGULAR_2D("2D"),
    REGULAR_3D("3D"),
    ADVANCE_PREVIEW_2D("2D"),
    ADVANCE_PREVIEW_3D("3D");

    private final String format;

    ScreeningType(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }

    public boolean is3D() {
        return this == REGULAR_3D || this == ADVANCE_PREVIEW_3D;
    }

    public boolean isRegular() {
        return this == REGULAR_2D || this == REGULAR_3D;
    }
}