package com.nexaiot.server.admin.dto;

import java.time.Instant;

public record AdminUserResponse(String id, String fullName, String email, String role, Instant createdAt) {
}
