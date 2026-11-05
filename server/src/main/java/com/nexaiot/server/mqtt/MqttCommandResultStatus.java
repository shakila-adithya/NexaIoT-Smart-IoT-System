package com.nexaiot.server.mqtt;

import java.time.Instant;

public record MqttCommandResultStatus(
        String deviceKey,
        String commandId,
        String correlationId,
        String status,
        String reason,
        Instant processedAt
) {
    public String reasonOrEmpty() {
        return reason == null ? "" : reason;
    }
}
