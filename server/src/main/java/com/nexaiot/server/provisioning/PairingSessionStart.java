package com.nexaiot.server.provisioning;

import java.time.Instant;

/**
 * Internal boundary for the one-time raw token. Do not use as a general
 * device response DTO.
 */
public record PairingSessionStart(
        String sessionId,
        String rawToken,
        Instant expiresAt,
        int maximumAttempts
) {
}
