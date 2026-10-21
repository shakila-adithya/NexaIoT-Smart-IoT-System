package com.nexaiot.server.provisioning.dto;

import java.time.Instant;

public record EnrollmentOperationResponse(
        String operationId,
        String deviceId,
        String pairingSessionId,
        String enrollmentAttemptId,
        String state,
        int version,
        Instant createdAt,
        Instant updatedAt,
        boolean reconciliationRequired,
        boolean brokerIdentityVerified,
        boolean credentialDelivered,
        boolean deviceVerified,
        String recoveryOutcome,
        String recoveryReason,
        boolean retrySafe,
        boolean manualActionRequired
) {
}
