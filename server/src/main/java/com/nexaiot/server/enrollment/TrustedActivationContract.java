package com.nexaiot.server.enrollment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Fail-closed activation gate with exact identity, freshness, and replay checks. */
public final class TrustedActivationContract {

    private final TrustedActivationVerifier verifier;
    private final Duration maximumAge;
    private final Clock clock;
    private final Set<String> consumedChallenges = new HashSet<>();

    public TrustedActivationContract(TrustedActivationVerifier verifier, Duration maximumAge) {
        this(verifier, maximumAge, Clock.systemUTC());
    }

    TrustedActivationContract(TrustedActivationVerifier verifier, Duration maximumAge, Clock clock) {
        if (verifier == null || maximumAge == null || maximumAge.isZero()
                || maximumAge.isNegative() || clock == null) {
            throw new IllegalArgumentException("Activation policy is invalid.");
        }
        this.verifier = verifier;
        this.maximumAge = maximumAge;
        this.clock = clock;
    }

    public synchronized boolean verify(ActivationProof proof, String expectedDeviceKey,
            String expectedOperationId, String expectedClientId) {
        if (proof == null || !expectedDeviceKey.equals(proof.deviceKey())
                || !expectedOperationId.equals(proof.enrollmentOperationId())
                || !expectedClientId.equals(proof.mqttClientId())
                || consumedChallenges.contains(proof.challenge())) {
            return false;
        }
        Instant now = Instant.now(clock);
        Duration age = Duration.between(proof.observedAt(), now);
        if (age.isNegative() || age.compareTo(maximumAge) > 0 || !verifier.verify(proof)) {
            return false;
        }
        consumedChallenges.add(proof.challenge());
        return true;
    }
}
