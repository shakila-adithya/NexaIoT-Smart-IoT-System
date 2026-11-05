package com.nexaiot.server.enrollment;

import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.nexaiot.server.enrollment.dto.CredentialDeliveryChallengeResponse;
import com.nexaiot.server.enrollment.dto.CredentialDeliveryRequest;
import com.nexaiot.server.enrollment.dto.CredentialDeliveryResponse;
import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/** One-time device-proof delivery; no owner or backend credential is accepted. */
@Service
public class CredentialDeliveryService {

    private final DeviceEnrollmentOperationRepository operationRepository;
    private final MongoTemplate mongoTemplate;
    private final CredentialEscrowKeyProvider keyProvider;
    private final Duration challengeLifetime;
    private final Clock clock;

    @Autowired
    public CredentialDeliveryService(
            DeviceEnrollmentOperationRepository operationRepository,
            MongoTemplate mongoTemplate,
            CredentialEscrowKeyProvider keyProvider,
            @Value("${provisioning.credential-delivery.challenge-lifetime-seconds:120}") long lifetimeSeconds
    ) {
        this(operationRepository, mongoTemplate, keyProvider,
                Duration.ofSeconds(lifetimeSeconds), Clock.systemUTC());
    }

    CredentialDeliveryService(DeviceEnrollmentOperationRepository operationRepository,
            MongoTemplate mongoTemplate, CredentialEscrowKeyProvider keyProvider,
            Duration challengeLifetime, Clock clock) {
        if (operationRepository == null || mongoTemplate == null || keyProvider == null
                || challengeLifetime == null || challengeLifetime.isZero()
                || challengeLifetime.isNegative() || clock == null) {
            throw new IllegalArgumentException("Credential delivery policy is invalid.");
        }
        this.operationRepository = operationRepository;
        this.mongoTemplate = mongoTemplate;
        this.keyProvider = keyProvider;
        this.challengeLifetime = challengeLifetime;
        this.clock = clock;
    }

    public CredentialDeliveryChallengeResponse issueChallenge(
            String deviceId, String operationId, String ownerEmail) {
        DeviceEnrollmentOperation operation = owned(deviceId, operationId, ownerEmail);
        Instant now = Instant.now(clock);
        requireEscrowReady(operation, now);
        if (operation.getCredentialDeliveryChallenge() != null
                && operation.getCredentialDeliveryChallengeExpiresAt() != null
                && now.isBefore(operation.getCredentialDeliveryChallengeExpiresAt())) {
            return challengeResponse(operation, operation.getCredentialDeliveryChallenge(),
                    operation.getCredentialDeliveryChallengeExpiresAt());
        }
        String challenge = randomChallenge();
        Instant expiresAt = now.plus(challengeLifetime).truncatedTo(ChronoUnit.MILLIS);
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(operation.getId()),
                Criteria.where("deviceId").is(operation.getDeviceId()),
                Criteria.where("ownerEmail").is(ownerEmail.trim().toLowerCase()),
                Criteria.where("state").is(EnrollmentState.CREDENTIAL_DELIVERY_PENDING),
                Criteria.where("version").is(operation.getVersion()),
                Criteria.where("credentialDelivered").is(false),
                Criteria.where("credentialEscrowExpiresAt").gt(now),
                Criteria.where("credentialEscrowCiphertext").exists(true),
                new Criteria().orOperator(
                        Criteria.where("credentialDeliveryChallenge").exists(false),
                        Criteria.where("credentialDeliveryChallengeExpiresAt").lte(now))));
        Update update = new Update()
                .set("credentialDeliveryChallenge", challenge)
                .set("credentialDeliveryChallengeExpiresAt", expiresAt)
                .set("updatedAt", now)
                .inc("version", 1);
        DeviceEnrollmentOperation updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                DeviceEnrollmentOperation.class);
        if (updated == null) {
            throw new EnrollmentConcurrencyException("Credential delivery challenge changed concurrently.");
        }
        return challengeResponse(updated, challenge, expiresAt);
    }

    public CredentialDeliveryResponse deliver(
            String deviceId, String operationId, CredentialDeliveryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Credential delivery proof is required.");
        }
        DeviceEnrollmentOperation operation = owned(deviceId, operationId, "");
        Instant now = Instant.now(clock);
        if (operation.isCredentialDelivered()
                || operation.getState() != EnrollmentState.CREDENTIAL_DELIVERY_PENDING) {
            throw new IllegalArgumentException("Credential delivery challenge is invalid or expired.");
        }
        requireEscrowReady(operation, now);
        if (operation.getCredentialDeliveryChallenge() == null
                || operation.getCredentialDeliveryChallengeExpiresAt() == null
                || !now.isBefore(operation.getCredentialDeliveryChallengeExpiresAt())
                || !operation.getCredentialDeliveryChallenge().equals(request.challenge())) {
            throw new IllegalArgumentException("Credential delivery challenge is invalid or expired.");
        }
        String expectedClientId = operation.getBrokerClientId();
        if (!expectedClientId.equals(request.mqttClientId())) {
            throw new IllegalArgumentException("Credential delivery identity is invalid.");
        }
        PublicKey publicKey = DeviceTrustProtocol.decodeP256(operation.getTrustAnchorPublicKey());
        if (!DeviceTrustProtocol.fingerprint(publicKey).equals(operation.getTrustAnchorFingerprint())) {
            throw new IllegalArgumentException("Registered trust anchor is invalid.");
        }
        String deviceKey = deviceKey(expectedClientId);
        byte[] message = DeviceTrustProtocol.deliveryMessage(
                deviceKey, operationId, operation.getOwnerEmail(), expectedClientId,
                operation.getTrustAnchorFingerprint(), operation.getCredentialDeliveryChallenge(),
                operation.getCredentialDeliveryChallengeExpiresAt().toString());
        verify(publicKey, request.signature(), message);

        GeneratedDeviceCredential credential = CredentialEscrowCipher.decrypt(
                keyProvider.keyFor(operation.getCredentialEscrowKeyVersion()),
                new CredentialEscrowCipher.EncryptedCredential(
                        operation.getCredentialEscrowNonce(), operation.getCredentialEscrowCiphertext(),
                        operation.getCredentialEscrowKeyVersion()), operationId, deviceId,
                operation.getBrokerUsername(), operation.getBrokerClientId());
        Query query = readyQuery(operation, operation.getOwnerEmail(), now)
                .addCriteria(Criteria.where("credentialDeliveryChallenge")
                        .is(operation.getCredentialDeliveryChallenge()))
                .addCriteria(Criteria.where("credentialDeliveryChallengeExpiresAt").gt(now))
                .addCriteria(Criteria.where("credentialEscrowCiphertext")
                        .is(operation.getCredentialEscrowCiphertext()));
        Update update = new Update()
                .set("state", EnrollmentState.DEVICE_VERIFICATION_PENDING)
                .set("credentialDelivered", true)
                .unset("credentialEscrowNonce")
                .unset("credentialEscrowCiphertext")
                .unset("credentialDeliveryChallenge")
                .unset("credentialDeliveryChallengeExpiresAt")
                .set("updatedAt", now)
                .inc("version", 1);
        if (mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), DeviceEnrollmentOperation.class) == null) {
            throw new EnrollmentConcurrencyException("Credential delivery was already redeemed or changed.");
        }
        return new CredentialDeliveryResponse(operationId, credential.username(),
                credential.clientId(), credential.password());
    }

    private DeviceEnrollmentOperation owned(String deviceId, String operationId, String ownerEmail) {
        if (deviceId == null || deviceId.isBlank() || operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("Enrollment identity is required.");
        }
        return operationRepository.findById(operationId)
                .filter(operation -> deviceId.equals(operation.getDeviceId()))
                .filter(operation -> ownerEmail.isBlank()
                        || ownerEmail.trim().equalsIgnoreCase(operation.getOwnerEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Enrollment operation not found."));
    }

    private void requireEscrowReady(DeviceEnrollmentOperation operation, Instant now) {
        if (operation.getState() != EnrollmentState.CREDENTIAL_DELIVERY_PENDING
                || operation.isCredentialDelivered()
                || operation.getTrustAnchorPublicKey() == null
                || operation.getTrustAnchorFingerprint() == null
                || operation.getCredentialEscrowCiphertext() == null
                || operation.getCredentialEscrowNonce() == null
                || operation.getCredentialEscrowKeyVersion() == null
                || operation.getCredentialEscrowExpiresAt() == null
                || !now.isBefore(operation.getCredentialEscrowExpiresAt())
                || operation.getBrokerUsername() == null
                || operation.getBrokerClientId() == null) {
            throw new IllegalStateException("Credential delivery is not safely available.");
        }
    }

    private Query readyQuery(DeviceEnrollmentOperation operation, String ownerEmail, Instant now) {
        return new Query(new Criteria().andOperator(
                Criteria.where("_id").is(operation.getId()),
                Criteria.where("deviceId").is(operation.getDeviceId()),
                Criteria.where("ownerEmail").is(ownerEmail.trim().toLowerCase()),
                Criteria.where("state").is(EnrollmentState.CREDENTIAL_DELIVERY_PENDING),
                Criteria.where("version").is(operation.getVersion()),
                Criteria.where("credentialDelivered").is(false),
                Criteria.where("credentialEscrowExpiresAt").gt(now),
                Criteria.where("credentialEscrowCiphertext").exists(true)));
    }

    private CredentialDeliveryChallengeResponse challengeResponse(
            DeviceEnrollmentOperation operation, String challenge, Instant expiresAt) {
        return new CredentialDeliveryChallengeResponse(operation.getId(), operation.getDeviceId(),
                operation.getOwnerEmail(), operation.getBrokerClientId(),
                operation.getTrustAnchorFingerprint(), challenge,
                expiresAt, "NexaIoT/CredentialDelivery/v1");
    }

    private void verify(PublicKey publicKey, String encodedSignature, byte[] message) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update(message);
            if (!signature.verify(Base64.getDecoder().decode(encodedSignature))) {
                throw new IllegalArgumentException("Credential delivery proof is invalid.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Credential delivery proof is invalid.", exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Credential delivery proof could not be verified.", exception);
        }
    }


    private String randomChallenge() {
        byte[] value = new byte[32];
        ThreadLocalRandom.current().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String deviceKey(String clientId) {
        String prefix = "nexaiot-device-";
        if (clientId == null || !clientId.startsWith(prefix) || clientId.length() == prefix.length()) {
            throw new IllegalArgumentException("Enrollment client identity is invalid.");
        }
        return clientId.substring(prefix.length());
    }
}
