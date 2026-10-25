package com.nexaiot.server.mqtt;

/** Minimal pending-command identity required to accept an acknowledgement. */
public record MqttPendingCommand(
        String deviceKey,
        String commandId,
        String correlationId,
        String capabilityKey
) {
    public MqttPendingCommand(String deviceKey, String commandId, String correlationId) {
        this(deviceKey, commandId, correlationId, "power");
    }

    public MqttPendingCommand {
        require(deviceKey, "Device key is required.");
        require(commandId, "Command id is required.");
        require(capabilityKey, "Capability key is required.");
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
