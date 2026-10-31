package com.nexaiot.server.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/**
 * Explicitly local/test-only bootstrap adapter. It is not a Spring bean and
 * cannot be reached over HTTP. Production credential delivery remains deferred.
 */
public final class LocalSimulatorBootstrapAdapter implements SimulatorBootstrapAdapter {

    private final boolean enabled;
    private final Duration grantLifetime;
    private final Clock clock;

    public LocalSimulatorBootstrapAdapter(boolean enabled) {
        this(enabled, Duration.ofSeconds(30), Clock.systemUTC());
    }

    LocalSimulatorBootstrapAdapter(boolean enabled, Duration grantLifetime, Clock clock) {
        if (grantLifetime == null || grantLifetime.isZero() || grantLifetime.isNegative()) {
            throw new IllegalArgumentException("Bootstrap grant lifetime must be positive.");
        }
        this.enabled = enabled;
        this.grantLifetime = grantLifetime;
        this.clock = clock;
    }

    @Override
    public SimulatorBootstrapGrant authorize(
            SimulatorDeviceIdentity identity,
            String ownerEmail,
            String enrollmentOperationId
    ) {
        requireEnabled();
        requireIdentity(identity);
        requireNonBlank(ownerEmail, "Bootstrap owner is required.");
        requireNonBlank(enrollmentOperationId, "Enrollment operation is required.");
        return new SimulatorBootstrapGrant(
                identity,
                ownerEmail.trim().toLowerCase(),
                enrollmentOperationId,
                Instant.now(clock).plus(grantLifetime));
    }

    @Override
    public SimulatorCredentialEnvelope deliver(
            SimulatorBootstrapGrant grant,
            SimulatorDeviceIdentity presentedIdentity,
            GeneratedDeviceCredential credential
    ) {
        requireEnabled();
        if (grant == null || presentedIdentity == null || credential == null
                || !grant.identity().equals(presentedIdentity)
                || !credential.username().equals(presentedIdentity.clientId())
                || !credential.clientId().equals(presentedIdentity.clientId())
                || credential.password() == null || credential.password().isBlank()
                || !grant.consume(Instant.now(clock))) {
            throw new IllegalArgumentException("Bootstrap authorization was rejected.");
        }
        return new SimulatorCredentialEnvelope(
                credential.username(), credential.clientId(), credential.password());
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Local simulator bootstrap is disabled.");
        }
    }

    private void requireIdentity(SimulatorDeviceIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("Simulator identity is required.");
        }
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
