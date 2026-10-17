package com.careerpilot.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "job_analysis")
public class JobAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    public UserAccount owner;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    public JobApplication application;
    @Column(nullable = false)
    public int score;
    @Column(length = 30000)
    public String resultJson;
    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    protected JobAnalysis() {
    }

    public JobAnalysis(UserAccount owner, JobApplication application, int score, String resultJson) {
        this.owner = owner;
        this.application = application;
        this.score = score;
        this.resultJson = resultJson;
    }
}

