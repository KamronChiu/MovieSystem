package com.eduaccess.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "screens",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"cinema_id", "screen_number"})
        }
)
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(name = "screen_number", nullable = false)
    private int screenNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "hall_type", nullable = false)
    private HallType hallType = HallType.REGULAR;

    @Column(nullable = false)
    private int capacity;

    @OneToMany(mappedBy = "screen", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();

    @OneToMany(mappedBy = "screen")
    private List<Screening> screenings = new ArrayList<>();

    protected Screen() {
    }

    public Screen(Cinema cinema, int screenNumber, int capacity, HallType hallType) {
        this.cinema = cinema;
        this.screenNumber = screenNumber;
        this.capacity = capacity;
        this.hallType = hallType == null ? HallType.REGULAR : hallType;
    }

    public Screen(Cinema cinema, int screenNumber, int capacity) {
        this(cinema, screenNumber, capacity, HallType.REGULAR);
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
        seat.setScreen(this);
    }

    public void removeSeat(Seat seat) {
        seats.remove(seat);
        seat.setScreen(null);
    }

    public Long getId() {
        return id;
    }

    public Cinema getCinema() {
        return cinema;
    }

    public int getScreenNumber() {
        return screenNumber;
    }

    public HallType getHallType() {
        return hallType == null ? HallType.REGULAR : hallType;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public List<Screening> getScreenings() {
        return screenings;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCinema(Cinema cinema) {
        this.cinema = cinema;
    }

    public void setScreenNumber(int screenNumber) {
        this.screenNumber = screenNumber;
    }

    public void setHallType(HallType hallType) {
        this.hallType = hallType == null ? HallType.REGULAR : hallType;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public void setSeats(List<Seat> seats) {
        this.seats = seats;
    }

    public void setScreenings(List<Screening> screenings) {
        this.screenings = screenings;
    }

    @Override
    public String toString() {
        return hallType.getLabel() + " - Screen " + screenNumber + " - " + capacity + " seats";
    }
}