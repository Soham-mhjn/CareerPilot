package com.careerpilot.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "application_status_history", indexes = {
        @Index(name = "idx_status_history_owner_changed", columnList = "owner_id,changed_at"),
        @Index(name = "idx_status_history_application_changed", columnList = "application_id,changed_at")
})
public class ApplicationStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    public UserAccount owner;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    public JobApplication application;

    @Column(name = "from_status", length = 30)
    public String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    public String toStatus;

    @Column(name = "changed_at", nullable = false)
    public Instant changedAt = Instant.now();

    protected ApplicationStatusHistory() {
    }

    public ApplicationStatusHistory(UserAccount owner, JobApplication application, String fromStatus, String toStatus) {
        this.owner = owner;
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = Instant.now();
    }
}
