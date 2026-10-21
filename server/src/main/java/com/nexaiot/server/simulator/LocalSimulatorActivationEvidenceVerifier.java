package com.nexaiot.server.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Explicitly local verifier for offline fixtures. It is not a production
 * device-authentication mechanism and is not registered as a Spring bean.
 */
public final class LocalSimulatorActivationEvidenceVerifier
        implements SimulatorActivationEvidenceVerifier {

    private final boolean enabled;
    private final String expectedOperationId;
    private final Duration maximumAge;
    private final Clock clock;

    public LocalSimulatorActivationEvidenceVerifier(
            boolean enabled, String expectedOperationId, Duration maximumAge) {
        this(enabled, expectedOperationId, maximumAge, Clock.systemUTC());
    }

    LocalSimulatorActivationEvidenceVerifier(
            boolean enabled,
            String expectedOperationId,
            Duration maximumAge,
            Clock clock
    ) {
        if (expectedOperationId == null || expectedOperationId.isBlank()
                || maximumAge == null || maximumAge.isZero() || maximumAge.isNegative()
                || clock == null) {
            throw new IllegalArgumentException("Activation verification policy is invalid.");
        }
        this.enabled = enabled;
        this.expectedOperationId = expectedOperationId;
        this.maximumAge = maximumAge;
        this.clock = clock;
    }

    @Override
    public boolean verify(SimulatorDeviceIdentity identity, SimulatorActivationEvidence evidence) {
        if (!enabled || identity == null || evidence == null
                || !identity.deviceKey().equals(evidence.deviceKey())
                || !expectedOperationId.equals(evidence.enrollmentOperationId())) {
            return false;
        }
        Instant now = Instant.now(clock);
        Duration age = Duration.between(evidence.observedAt(), now);
        return !age.isNegative() && age.compareTo(maximumAge) <= 0;
    }
}
