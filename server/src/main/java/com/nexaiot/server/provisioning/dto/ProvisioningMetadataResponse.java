package com.nexaiot.server.provisioning.dto;

import java.time.Instant;

public record ProvisioningMetadataResponse(
        String deviceId,
        String deviceKey,
        String status,
        Instant provisionedAt,
        Instant lastProvisioningAttemptAt,
        String activePairingSessionId,
        Instant activePairingSessionExpiresAt
) {
}
