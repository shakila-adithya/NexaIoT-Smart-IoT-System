package com.nexaiot.server.provisioning.dto;

import java.time.Instant;

public record PairingClaimResponse(
        String sessionId,
        String status,
        Instant expiresAt
) {
}
