package com.nexaiot.server.enrollment;

import java.time.Instant;

/** Backend activation evidence bound to the MQTT identity and a one-time challenge. */
public record ActivationProof(
        String deviceKey,
        String enrollmentOperationId,
        String mqttClientId,
        String challenge,
        Instant observedAt
) {
    public ActivationProof {
        require(deviceKey, "Device key is required.");
        require(enrollmentOperationId, "Enrollment operation is required.");
        require(mqttClientId, "MQTT client identity is required.");
        require(challenge, "Activation challenge is required.");
        if (observedAt == null) {
            throw new IllegalArgumentException("Activation proof time is required.");
        }
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
