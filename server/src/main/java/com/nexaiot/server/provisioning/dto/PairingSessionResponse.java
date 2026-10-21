package com.nexaiot.server.provisioning.dto;

import java.time.Instant;

/** Safe session metadata. It intentionally contains neither token nor token hash. */
public record PairingSessionResponse(
        String sessionId,
        String deviceId,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant consumedAt,
        int attemptCount,
        int maximumAttempts,
        String failureReason
) {
}
