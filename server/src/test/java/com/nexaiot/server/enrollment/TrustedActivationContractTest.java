package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TrustedActivationContractTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DEVICE = "NEXA-ACTIVE-1";
    private static final String OPERATION = "operation-1";
    private static final String CLIENT = "nexaiot-device-" + DEVICE;

    @Test void validFreshBackendProofActivatesOnce() {
        TrustedActivationContract contract = contract(proof -> true);
        ActivationProof proof = proof("challenge-1", NOW.minusSeconds(5));
        assertTrue(contract.verify(proof, DEVICE, OPERATION, CLIENT));
        assertFalse(contract.verify(proof, DEVICE, OPERATION, CLIENT));
    }

    @Test void wrongDeviceOperationOrClientIsRejected() {
        TrustedActivationContract contract = contract(proof -> true);
        ActivationProof proof = proof("challenge-1", NOW);
        assertFalse(contract.verify(proof, "NEXA-OTHER", OPERATION, CLIENT));
        assertFalse(contract.verify(proof("challenge-2", NOW), DEVICE, "other", CLIENT));
        assertFalse(contract.verify(proof("challenge-3", NOW), DEVICE, OPERATION, "other"));
    }

    @Test void staleFutureAndUntrustedProofsAreRejected() {
        TrustedActivationContract contract = contract(proof -> true);
        assertFalse(contract.verify(proof("old", NOW.minusSeconds(31)), DEVICE, OPERATION, CLIENT));
        assertFalse(contract.verify(proof("future", NOW.plusSeconds(1)), DEVICE, OPERATION, CLIENT));
        assertFalse(contract(proof -> false).verify(proof("untrusted", NOW), DEVICE, OPERATION, CLIENT));
    }

    @Test void defaultTrustAdapterRejectsWithoutDeviceTrustMechanism() {
        TrustedActivationContract contract = contract(TrustedActivationVerifier.rejectAll());
        assertFalse(contract.verify(proof("no-anchor", NOW), DEVICE, OPERATION, CLIENT));
    }

    private TrustedActivationContract contract(TrustedActivationVerifier verifier) {
        return new TrustedActivationContract(verifier, Duration.ofSeconds(30),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ActivationProof proof(String challenge, Instant observedAt) {
        return new ActivationProof(DEVICE, OPERATION, CLIENT, challenge, observedAt);
    }
}
