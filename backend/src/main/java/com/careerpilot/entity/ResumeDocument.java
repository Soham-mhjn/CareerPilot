package com.careerpilot.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "resume_document")
public class ResumeDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    public UserAccount owner;
    @Column(nullable = false)
    public String fileName;
    @Column(nullable = false)
    public String storageName;
    @Column(nullable = false)
    public String versionName;
    @Column(length = 300)
    public String targetRole;
    @Column(length = 100000)
    public String extractedText;
    @Column(nullable = false)
    public long fileSize;
    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    protected ResumeDocument() {
    }

    public ResumeDocument(UserAccount owner, String fileName, String storageName, String versionName, String targetRole, String extractedText, long fileSize) {
        this.owner = owner;
        this.fileName = fileName;
        this.storageName = storageName;
        this.versionName = versionName;
        this.targetRole = targetRole;
        this.extractedText = extractedText;
        this.fileSize = fileSize;
    }
}

