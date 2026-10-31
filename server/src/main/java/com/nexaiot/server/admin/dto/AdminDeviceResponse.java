package com.nexaiot.server.admin.dto;

import java.time.Instant;

public record AdminDeviceResponse(String id, String deviceKey, String name, String type, String status, String ownerEmail, Instant createdAt, Instant lastSeenAt) {
}
