package com.nexaiot.server.simulator;

import java.time.Instant;

/** Non-secret activation observation supplied by an authenticated verifier. */
public record SimulatorActivationEvidence(
        String deviceKey,
        String enrollmentOperationId,
        Instant observedAt,
        String proofReference
) {

    public SimulatorActivationEvidence {
        if (deviceKey == null || deviceKey.isBlank()
                || enrollmentOperationId == null || enrollmentOperationId.isBlank()
                || observedAt == null || proofReference == null || proofReference.isBlank()) {
            throw new IllegalArgumentException("Activation evidence is incomplete.");
        }
    }
}
