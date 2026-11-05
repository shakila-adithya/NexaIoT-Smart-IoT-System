package com.nexaiot.server.mqtt.management;

import java.time.Instant;
import java.util.UUID;

/** Non-secret evidence for one manually authorized integration run. */
record IntegrationRunReceipt(
        String runId,
        String deviceKey,
        String username,
        String clientId,
        String roleName,
        boolean clientCreationConfirmed,
        boolean roleCreationConfirmed,
        String reconciliationStatus,
        Instant createdAt
) {
    static IntegrationRunReceipt create(
            String deviceKey,
            String username,
            String clientId,
            String roleName
    ) {
        return new IntegrationRunReceipt(
                UUID.randomUUID().toString(), deviceKey, username, clientId, roleName,
                false, false, "NOT_STARTED", Instant.now());
    }
}
