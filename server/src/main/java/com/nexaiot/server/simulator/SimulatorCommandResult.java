package com.nexaiot.server.simulator;

import java.time.Instant;

public record SimulatorCommandResult(
        String commandId,
        String correlationId,
        String capabilityKey,
        Status status,
        String reason,
        Instant processedAt
) {

    public enum Status {
        ACCEPTED,
        DUPLICATE,
        REJECTED
    }
}
