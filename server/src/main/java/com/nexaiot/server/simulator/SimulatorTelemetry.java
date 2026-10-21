package com.nexaiot.server.simulator;

import java.time.Instant;
import java.util.Map;

/** Capability-based telemetry envelope; values are validated by the simulator fixture. */
public record SimulatorTelemetry(
        String deviceKey,
        Instant capturedAt,
        Map<String, Object> values
) {

    public SimulatorTelemetry {
        if (deviceKey == null || deviceKey.isBlank() || capturedAt == null
                || values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Simulator telemetry is incomplete.");
        }
        values = Map.copyOf(values);
    }
}
