package com.nexaiot.server.provisioning.dto;

import java.time.Instant;

/** The raw token is returned only at creation, for the authenticated owner to transfer securely. */
public record PairingSessionStartResponse(
        String sessionId,
        String pairingToken,
        Instant expiresAt,
        int maximumAttempts
) {
}
