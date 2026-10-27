package com.nexaiot.server.enrollment.dto;

import java.time.Instant;

public record TrustAnchorRegistrationResponse(
        String operationId,
        String fingerprint,
        Instant registeredAt,
        String protocol
) {
}
