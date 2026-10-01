package com.nexaiot.server.dto;

public class UserResponse {

    private String id;
    private String fullName;
    private String email;
    private String role;

    public UserResponse(
            String id,
            String fullName,
            String email,
            String role
    ) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}