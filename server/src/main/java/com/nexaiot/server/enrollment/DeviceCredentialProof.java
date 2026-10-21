package com.nexaiot.server.enrollment;

import java.time.Instant;

/** Device proof used by the future authenticated credential handoff. */
public record DeviceCredentialProof(
        String deviceKey,
        String enrollmentOperationId,
        String publicKeyFingerprint,
        String challenge,
        String signature,
        Instant createdAt
) {
    public DeviceCredentialProof {
        require(deviceKey, "Device key is required.");
        require(enrollmentOperationId, "Enrollment operation is required.");
        require(publicKeyFingerprint, "Device trust anchor is required.");
        require(challenge, "Credential delivery challenge is required.");
        require(signature, "Credential delivery proof is required.");
        if (createdAt == null) {
            throw new IllegalArgumentException("Credential delivery proof time is required.");
        }
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
