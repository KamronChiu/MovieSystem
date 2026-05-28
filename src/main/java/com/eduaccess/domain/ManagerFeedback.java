package com.eduaccess.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "manager_feedback")
public class ManagerFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "manager_username", nullable = false)
    private String managerUsername;

    @Column(name = "manager_name")
    private String managerName;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "comment", length = 2500)
    private String comment;

    protected ManagerFeedback() {
    }

    public ManagerFeedback(String managerUsername, String managerName, String title, String comment) {
        this.managerUsername = managerUsername == null || managerUsername.isBlank() ? "manager" : managerUsername;
        this.managerName = managerName;
        this.title = title == null || title.isBlank() ? "Manager feedback" : title;
        this.comment = comment;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    private void beforeInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getManagerUsername() {
        return managerUsername;
    }

    public String getManagerName() {
        return managerName;
    }

    public String getTitle() {
        return title;
    }

    public String getComment() {
        return comment;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setManagerUsername(String managerUsername) {
        this.managerUsername = managerUsername;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
