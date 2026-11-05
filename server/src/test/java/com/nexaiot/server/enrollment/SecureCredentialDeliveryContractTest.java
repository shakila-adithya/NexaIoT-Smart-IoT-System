package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

class SecureCredentialDeliveryContractTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DEVICE = "NEXA-CONTRACT-1";
    private static final String OPERATION = "operation-1";

    @Test void validProofDeliversOnlyTheBoundCredential() {
        DeviceCredentialProofVerifier verifier = proof -> proof.publicKeyFingerprint().equals("fp-1");
        SecureCredentialDeliveryContract contract = contract(verifier, NOW);
        CredentialDeliveryGrant grant = contract.issueGrant(DEVICE, OPERATION, true);
        GeneratedDeviceCredential credential = contract.deliver(grant,
                proof(grant, DEVICE, OPERATION, "fp-1"), credential());
        assertEquals("nexaiot-device-" + DEVICE, credential.username());
    }

    @Test void wrongDeviceIsRejected() {
        SecureCredentialDeliveryContract contract = contract(proof -> true, NOW);
        CredentialDeliveryGrant grant = contract.issueGrant(DEVICE, OPERATION, true);
        assertThrows(IllegalArgumentException.class, () -> contract.deliver(grant,
                proof(grant, "NEXA-OTHER", OPERATION, "fp"), credential()));
    }

    @Test void wrongOperationIsRejected() {
        SecureCredentialDeliveryContract contract = contract(proof -> true, NOW);
        CredentialDeliveryGrant grant = contract.issueGrant(DEVICE, OPERATION, true);
        assertThrows(IllegalArgumentException.class, () -> contract.deliver(grant,
                proof(grant, DEVICE, "operation-other", "fp"), credential()));
    }

    @Test void replayIsRejected() {
        SecureCredentialDeliveryContract contract = contract(proof -> true, NOW);
        CredentialDeliveryGrant grant = contract.issueGrant(DEVICE, OPERATION, true);
        contract.deliver(grant, proof(grant, DEVICE, OPERATION, "fp"), credential());
        assertThrows(IllegalArgumentException.class, () -> contract.deliver(grant,
                proof(grant, DEVICE, OPERATION, "fp"), credential()));
    }

    @Test void expiredGrantIsRejected() {
        Clock clock = Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC);
        SecureCredentialDeliveryContract contract = new SecureCredentialDeliveryContract(
                proof -> true, Duration.ofSeconds(30), clock);
        CredentialDeliveryGrant grant = new CredentialDeliveryGrant(DEVICE, OPERATION,
                "challenge", NOW.plusSeconds(30));
        assertThrows(IllegalArgumentException.class, () -> contract.deliver(grant,
                proof(grant, DEVICE, OPERATION, "fp"), credential()));
    }

    @Test void missingTrustAnchorFailsClosed() {
        SecureCredentialDeliveryContract contract = contract(DeviceCredentialProofVerifier.rejectAll(), NOW);
        CredentialDeliveryGrant grant = contract.issueGrant(DEVICE, OPERATION, true);
        assertThrows(IllegalArgumentException.class, () -> contract.deliver(grant,
                proof(grant, DEVICE, OPERATION, "untrusted"), credential()));
    }

    @Test void unauthorizedEnrollmentCannotIssueGrant() {
        SecureCredentialDeliveryContract contract = contract(proof -> true, NOW);
        assertThrows(IllegalArgumentException.class,
                () -> contract.issueGrant(DEVICE, OPERATION, false));
    }

    @Test void noPasswordIsIncludedInGrantOrProofText() {
        SecureCredentialDeliveryContract contract = contract(proof -> true, NOW);
        CredentialDeliveryGrant grant = contract.issueGrant(DEVICE, OPERATION, true);
        DeviceCredentialProof proof = proof(grant, DEVICE, OPERATION, "fp");
        assertFalse(grant.toString().contains("password"));
        assertFalse(proof.toString().contains("password"));
        assertTrue(credential().toString().contains("<redacted>"));
    }

    private SecureCredentialDeliveryContract contract(DeviceCredentialProofVerifier verifier,
            Instant now) {
        return new SecureCredentialDeliveryContract(verifier, Duration.ofSeconds(30),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private DeviceCredentialProof proof(CredentialDeliveryGrant grant, String device,
            String operation, String fingerprint) {
        return new DeviceCredentialProof(device, operation, fingerprint, grant.challenge(),
                "signature", NOW);
    }

    private GeneratedDeviceCredential credential() {
        return new GeneratedDeviceCredential("nexaiot-device-" + DEVICE,
                "nexaiot-device-" + DEVICE, "test-password");
    }
}
