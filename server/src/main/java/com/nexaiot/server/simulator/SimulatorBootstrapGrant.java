package com.nexaiot.server.simulator;

import java.time.Instant;
import java.util.UUID;

/** Opaque, single-use authorization state; it contains no MQTT secret. */
public final class SimulatorBootstrapGrant {

    private final String grantId = UUID.randomUUID().toString();
    private final SimulatorDeviceIdentity identity;
    private final String ownerEmail;
    private final String enrollmentOperationId;
    private final Instant expiresAt;
    private boolean consumed;

    SimulatorBootstrapGrant(
            SimulatorDeviceIdentity identity,
            String ownerEmail,
            String enrollmentOperationId,
            Instant expiresAt
    ) {
        this.identity = identity;
        this.ownerEmail = ownerEmail;
        this.enrollmentOperationId = enrollmentOperationId;
        this.expiresAt = expiresAt;
    }

    public String grantId() { return grantId; }
    public SimulatorDeviceIdentity identity() { return identity; }
    public String ownerEmail() { return ownerEmail; }
    public String enrollmentOperationId() { return enrollmentOperationId; }
    public Instant expiresAt() { return expiresAt; }

    synchronized boolean consume(Instant now) {
        if (consumed || !now.isBefore(expiresAt)) {
            return false;
        }
        consumed = true;
        return true;
    }
}
