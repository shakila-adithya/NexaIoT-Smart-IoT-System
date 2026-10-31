package com.nexaiot.server.provisioning;

import java.time.Instant;

public record PairingClaimResult(
        PairingClaimStatus status,
        String sessionId,
        Instant expiresAt
) {
}
