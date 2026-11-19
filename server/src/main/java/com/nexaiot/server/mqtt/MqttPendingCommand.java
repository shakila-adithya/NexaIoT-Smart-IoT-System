package com.nexaiot.server.mqtt;

import java.time.Instant;

/** Minimal pending-command identity required to accept an acknowledgement. */
public record MqttPendingCommand(
        String deviceKey,
        String commandId,
        String correlationId,
        String capabilityKey,
        Instant issuedAt,
        Instant expiresAt
) {
    public MqttPendingCommand(String deviceKey, String commandId, String correlationId, String capabilityKey) {
        this(deviceKey, commandId, correlationId, capabilityKey, Instant.now(), null);
    }

    public MqttPendingCommand(String deviceKey, String commandId, String correlationId) {
        this(deviceKey, commandId, correlationId, "power");
    }

    public MqttPendingCommand {
        require(deviceKey, "Device key is required.");
        require(commandId, "Command id is required.");
        require(capabilityKey, "Capability key is required.");
        if (issuedAt == null) throw new IllegalArgumentException("Command issue time is required.");
        if (expiresAt == null) expiresAt = issuedAt.plusSeconds(30);
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("Command deadline must follow issue time.");
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
