package com.nexaiot.server.dto;

import jakarta.validation.constraints.NotBlank;

public class DeleteAccountRequest {
    @NotBlank(message = "Password is required")
    private String password;
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
