package com.careerpilot.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "interview_question")
public class InterviewQuestion {
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
    public String category;
    @Column(nullable = false)
    public String difficulty;
    @Column(nullable = false, length = 2000)
    public String question;
    @Column(length = 1000)
    public String relevance;
    @Column(length = 3000)
    public String answerStructure;
    @Column(length = 8000)
    public String practiceAnswer;
    @Column(length = 3000)
    public String selfAssessment;
    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    protected InterviewQuestion() {
    }

    public InterviewQuestion(UserAccount u, JobApplication a, String c, String d, String q, String r, String structure) {
        owner = u;
        application = a;
        category = c;
        difficulty = d;
        question = q;
        relevance = r;
        answerStructure = structure;
    }
}
