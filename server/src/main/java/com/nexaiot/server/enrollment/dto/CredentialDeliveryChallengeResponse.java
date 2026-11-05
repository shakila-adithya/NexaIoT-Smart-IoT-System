package com.nexaiot.server.enrollment.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CredentialDeliveryChallengeResponse(
        String operationId,
        String deviceId,
        String ownerEmail,
        String mqttClientId,
        String trustAnchorFingerprint,
        String challenge,
        @JsonSerialize(using = ToStringSerializer.class) Instant expiresAt,
        String protocol
) {
}
