package com.careerpilot.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "workspace_item")
public class WorkspaceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    public UserAccount owner;
    @Column(nullable = false)
    public String type;
    @Column(nullable = false)
    public String title;
    @Column(length = 5000)
    public String notes;
    public String company;
    public Instant startsAt;
    public Integer durationMinutes;
    public String meetingUrl;
    @Column(nullable = false)
    public boolean completed = false;
    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    protected WorkspaceItem() {
    }

    public WorkspaceItem(UserAccount owner, String type, String title, String notes, String company, Instant startsAt, Integer durationMinutes, String meetingUrl) {
        this.owner = owner;
        this.type = type;
        this.title = title;
        this.notes = notes;
        this.company = company;
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
        this.meetingUrl = meetingUrl;
    }
}

