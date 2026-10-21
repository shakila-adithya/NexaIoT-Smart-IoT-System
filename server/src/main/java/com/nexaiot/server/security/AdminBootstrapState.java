package com.nexaiot.server.security;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "security_bootstrap_state")
public class AdminBootstrapState {

    @Id
    private String id;
    private String email;
    private String status;
    private Instant createdAt;
    private Instant completedAt;

    public AdminBootstrapState() {
    }

    public AdminBootstrapState(String id, String email, String status, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
