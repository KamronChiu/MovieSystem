package com.eduaccess.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "films")
public class Film {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1500)
    private String description;

    @Column(length = 1000)
    private String actors;

    @Column(length = 1000)
    private String directors;

    @Column(nullable = false)
    private String genre;

    @Column(name = "age_rating", nullable = false)
    private String ageRating;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "content_advice", length = 1000)
    private String contentAdvice;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @OneToMany(mappedBy = "film")
    private List<Screening> screenings = new ArrayList<>();

    protected Film() {
    }

    public Film(
            String title,
            String description,
            String actors,
            String genre,
            String ageRating,
            int durationMinutes
    ) {
        this.title = title;
        this.description = description;
        this.actors = actors;
        this.genre = genre;
        this.ageRating = ageRating;
        this.durationMinutes = durationMinutes;
    }

    public Film(
            String title,
            String description,
            String actors,
            String directors,
            String genre,
            String ageRating,
            int durationMinutes,
            LocalDate releaseDate,
            String contentAdvice,
            String posterUrl
    ) {
        this.title = title;
        this.description = description;
        this.actors = actors;
        this.directors = directors;
        this.genre = genre;
        this.ageRating = ageRating;
        this.durationMinutes = durationMinutes;
        this.releaseDate = releaseDate;
        this.contentAdvice = contentAdvice;
        this.posterUrl = posterUrl;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getActors() {
        return actors;
    }

    public String getDirectors() {
        return directors;
    }

    public String getGenre() {
        return genre;
    }

    public String getAgeRating() {
        return ageRating;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public String getContentAdvice() {
        return contentAdvice;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public List<Screening> getScreenings() {
        return screenings;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setActors(String actors) {
        this.actors = actors;
    }

    public void setDirectors(String directors) {
        this.directors = directors;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public void setAgeRating(String ageRating) {
        this.ageRating = ageRating;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public void setContentAdvice(String contentAdvice) {
        this.contentAdvice = contentAdvice;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public void setScreenings(List<Screening> screenings) {
        this.screenings = screenings;
    }

    @Override
    public String toString() {
        return title + " (" + ageRating + ")";
    }
}