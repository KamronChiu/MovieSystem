package com.eduaccess.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "screenings")
public class Screening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "film_id", nullable = false)
    private Film film;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "screening_date", nullable = false)
    private LocalDate screeningDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "screening_type", length = 30)
    private ScreeningType screeningType = ScreeningType.REGULAR_2D;

    protected Screening() {
    }

    public Screening(Film film, Screen screen, LocalDate screeningDate, LocalTime startTime) {
        this(film, screen, screeningDate, startTime, ScreeningType.REGULAR_2D);
    }

    public Screening(
            Film film,
            Screen screen,
            LocalDate screeningDate,
            LocalTime startTime,
            ScreeningType screeningType
    ) {
        this.film = film;
        this.screen = screen;
        this.screeningDate = screeningDate;
        this.startTime = startTime;
        this.endTime = startTime.plusMinutes(film.getDurationMinutes());
        this.screeningType = screeningType == null ? ScreeningType.REGULAR_2D : screeningType;
    }

    @PrePersist
    @PreUpdate
    private void ensureDefaults() {
        if (screeningType == null) {
            screeningType = ScreeningType.REGULAR_2D;
        }

        if (film != null && startTime != null) {
            endTime = startTime.plusMinutes(film.getDurationMinutes());
        }
    }

    public Long getId() {
        return id;
    }

    public Film getFilm() {
        return film;
    }

    public Screen getScreen() {
        return screen;
    }

    public LocalDate getScreeningDate() {
        return screeningDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public ScreeningType getScreeningType() {
        return screeningType == null ? ScreeningType.REGULAR_2D : screeningType;
    }

    public Cinema getCinema() {
        return screen.getCinema();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setFilm(Film film) {
        this.film = film;
        if (this.startTime != null && film != null) {
            this.endTime = this.startTime.plusMinutes(film.getDurationMinutes());
        }
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    public void setScreeningDate(LocalDate screeningDate) {
        this.screeningDate = screeningDate;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
        if (film != null && startTime != null) {
            this.endTime = startTime.plusMinutes(film.getDurationMinutes());
        }
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public void setScreeningType(ScreeningType screeningType) {
        this.screeningType = screeningType == null ? ScreeningType.REGULAR_2D : screeningType;
    }

    @Override
    public String toString() {
        return film.getTitle() + " - " + screeningDate + " " + startTime;
    }

    public String getFormat() {
        return screeningType == null ? "2D" : screeningType.getFormat();
    }

    public boolean is3D() {
        return screeningType != null && screeningType.is3D();
    }

    public boolean isRegular() {
        return screeningType == null || screeningType.isRegular();
    }
}
