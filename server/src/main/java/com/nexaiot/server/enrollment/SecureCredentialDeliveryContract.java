package com.nexaiot.server.enrollment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/**
 * Transport-neutral contract for a future authenticated device handoff.
 * It is deliberately not a controller or Spring endpoint. Without a trusted
 * device proof verifier, every delivery attempt is rejected.
 */
public final class SecureCredentialDeliveryContract {

    private final DeviceCredentialProofVerifier proofVerifier;
    private final Duration grantLifetime;
    private final Clock clock;

    public SecureCredentialDeliveryContract(DeviceCredentialProofVerifier proofVerifier,
            Duration grantLifetime) {
        this(proofVerifier, grantLifetime, Clock.systemUTC());
    }

    SecureCredentialDeliveryContract(DeviceCredentialProofVerifier proofVerifier,
            Duration grantLifetime, Clock clock) {
        if (proofVerifier == null || grantLifetime == null || grantLifetime.isZero()
                || grantLifetime.isNegative() || clock == null) {
            throw new IllegalArgumentException("Credential delivery policy is invalid.");
        }
        this.proofVerifier = proofVerifier;
        this.grantLifetime = grantLifetime;
        this.clock = clock;
    }

    public CredentialDeliveryGrant issueGrant(String deviceKey, String enrollmentOperationId,
            boolean authorizedEnrollmentContext) {
        require(deviceKey, "Device key is required.");
        require(enrollmentOperationId, "Enrollment operation is required.");
        if (!authorizedEnrollmentContext) {
            throw new IllegalArgumentException("Enrollment authorization is required.");
        }
        Instant now = Instant.now(clock);
        return new CredentialDeliveryGrant(deviceKey, enrollmentOperationId,
                UUID.randomUUID().toString(), now.plus(grantLifetime));
    }

    public GeneratedDeviceCredential deliver(CredentialDeliveryGrant grant,
            DeviceCredentialProof proof, GeneratedDeviceCredential credential) {
        if (grant == null || proof == null || credential == null
                || !grant.deviceKey().equals(proof.deviceKey())
                || !grant.enrollmentOperationId().equals(proof.enrollmentOperationId())
                || !grant.challenge().equals(proof.challenge())
                || !credential.username().equals("nexaiot-device-" + grant.deviceKey())
                || !credential.clientId().equals(credential.username())
                || credential.password() == null || credential.password().isBlank()
                || !proofVerifier.verify(proof)
                || !grant.consume(Instant.now(clock))) {
            throw new IllegalArgumentException("Authenticated credential delivery was rejected.");
        }
        return credential;
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
