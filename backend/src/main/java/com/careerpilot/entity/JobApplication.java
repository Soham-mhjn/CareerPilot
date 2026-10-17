package com.careerpilot.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "job_application", indexes = @Index(name = "idx_application_owner", columnList = "owner_id"))
public class JobApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    public UserAccount owner;
    @Column(nullable = false)
    public String company;
    @Column(nullable = false)
    public String role;
    public String location;
    @Column(length = 12000)
    public String jobDescription;
    public String postingUrl;
    @Column(nullable = false)
    public String status = "SAVED";
    public String priority = "MEDIUM";
    public String source;
    @Column(nullable = false)
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    protected JobApplication() {
    }

    public JobApplication(UserAccount owner, String company, String role, String location, String jobDescription, String postingUrl, String status, String priority, String source) {
        this.owner = owner;
        this.company = company;
        this.role = role;
        this.location = location;
        this.jobDescription = jobDescription;
        this.postingUrl = postingUrl;
        this.status = status;
        this.priority = priority;
        this.source = source;
    }
}


