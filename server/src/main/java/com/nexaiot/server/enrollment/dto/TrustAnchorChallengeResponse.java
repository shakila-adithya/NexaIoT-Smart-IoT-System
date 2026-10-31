package com.nexaiot.server.enrollment.dto;

import java.time.Instant;

public record TrustAnchorChallengeResponse(
        String operationId,
        String deviceId,
        String mqttClientId,
        String challenge,
        Instant expiresAt,
        String protocol
) {
}
