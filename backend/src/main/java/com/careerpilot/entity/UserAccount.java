package com.careerpilot.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_user")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true)
    public String email;
    @Column(name = "full_name", nullable = false)
    public String fullName;
    @Column(name = "password_hash", nullable = false)
    public String passwordHash;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    protected UserAccount() {
    }

    public UserAccount(String email, String fullName, String passwordHash) {
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
    }
}



