package com.nexaiot.server.simulator;

import java.util.Map;

/** Capability-neutral command envelope received on the device command topic. */
public record SimulatorCommand(String capabilityKey, Object value, Map<String, Object> metadata) {

    public SimulatorCommand {
        if (capabilityKey == null || capabilityKey.isBlank()) {
            throw new IllegalArgumentException("Command capability is required.");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
