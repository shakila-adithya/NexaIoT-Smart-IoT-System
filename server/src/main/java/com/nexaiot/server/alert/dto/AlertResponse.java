package com.nexaiot.server.alert.dto;

import java.time.Instant;

import com.nexaiot.server.alert.AlertSeverity;
import com.nexaiot.server.alert.AlertStatus;
import com.nexaiot.server.alert.AlertType;

public record AlertResponse(
        String id,
        String deviceId,
        String deviceKey,
        String deviceName,
        AlertType type,
        AlertSeverity severity,
        String message,
        Double value,
        Double threshold,
        AlertStatus status,
        Instant createdAt,
        Instant acknowledgedAt,
        Instant resolvedAt
) {
}
