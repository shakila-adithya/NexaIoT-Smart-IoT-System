package com.nexaiot.server.enrollment;

import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.nexaiot.server.enrollment.dto.TrustAnchorChallengeResponse;
import com.nexaiot.server.enrollment.dto.TrustAnchorRegistrationRequest;
import com.nexaiot.server.enrollment.dto.TrustAnchorRegistrationResponse;

/** Owner-gated public-key registration; it never handles MQTT credentials. */
@Service
public class DeviceTrustAnchorService {

    @SuppressWarnings("unused")
    private static final Duration DEFAULT_CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    private final DeviceEnrollmentOperationRepository operationRepository;
    private final MongoTemplate mongoTemplate;
    private final Duration challengeLifetime;
    private final Clock clock;

    @Autowired
    public DeviceTrustAnchorService(
            DeviceEnrollmentOperationRepository operationRepository,
            MongoTemplate mongoTemplate,
            @Value("${provisioning.trust-anchor.challenge-lifetime-seconds:300}") long lifetimeSeconds
    ) {
        this(operationRepository, mongoTemplate, Duration.ofSeconds(lifetimeSeconds), Clock.systemUTC());
    }

    DeviceTrustAnchorService(
            DeviceEnrollmentOperationRepository operationRepository,
            MongoTemplate mongoTemplate,
            Duration challengeLifetime,
            Clock clock
    ) {
        if (operationRepository == null || mongoTemplate == null || challengeLifetime == null
                || challengeLifetime.isZero() || challengeLifetime.isNegative() || clock == null) {
            throw new IllegalArgumentException("Trust-anchor policy is invalid.");
        }
        this.operationRepository = operationRepository;
        this.mongoTemplate = mongoTemplate;
        this.challengeLifetime = challengeLifetime;
        this.clock = clock;
    }

    public TrustAnchorChallengeResponse issueChallenge(
            String deviceId, String operationId, String ownerEmail
    ) {
        DeviceEnrollmentOperation operation = ownedOperation(deviceId, operationId, ownerEmail);
        requireReady(operation);
        Instant now = Instant.now(clock);
        if (operation.getTrustAnchorChallenge() != null
                && operation.getTrustAnchorChallengeExpiresAt() != null
                && now.isBefore(operation.getTrustAnchorChallengeExpiresAt())) {
            return response(operation, operation.getTrustAnchorChallenge(),
                    operation.getTrustAnchorChallengeExpiresAt());
        }

        String challenge = randomChallenge();
        Instant expiresAt = now.plus(challengeLifetime);
        Query query = new Query(new Criteria().andOperator(
                baseCriteria(operation, ownerEmail),
                new Criteria().orOperator(
                        Criteria.where("trustAnchorChallenge").exists(false),
                        Criteria.where("trustAnchorChallengeExpiresAt").lte(now)),
                Criteria.where("trustAnchorFingerprint").exists(false)));
        Update update = new Update()
                .set("trustAnchorChallenge", challenge)
                .set("trustAnchorChallengeExpiresAt", expiresAt)
                .inc("version", 1)
                .set("updatedAt", now);
        DeviceEnrollmentOperation updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                DeviceEnrollmentOperation.class);
        if (updated == null) {
            throw new EnrollmentConcurrencyException("Trust-anchor challenge changed concurrently.");
        }
        return response(updated, challenge, expiresAt);
    }

    public TrustAnchorRegistrationResponse register(
            String deviceId,
            String operationId,
            String ownerEmail,
            TrustAnchorRegistrationRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Trust-anchor registration is required.");
        }
        DeviceEnrollmentOperation operation = ownedOperation(deviceId, operationId, ownerEmail);
        requireReady(operation);
        if (operation.getTrustAnchorFingerprint() != null) {
            throw new IllegalArgumentException("A trust anchor is already registered.");
        }
        String expectedClientId = DeviceTrustProtocol.expectedClientId(
                deviceKeyFromClientId(operation.getBrokerClientId()));
        if (!expectedClientId.equals(operation.getBrokerClientId())
                || !expectedClientId.equals(request.mqttClientId())) {
            throw new IllegalArgumentException("MQTT client identity is not bound to the enrollment.");
        }
        if (operation.getTrustAnchorChallenge() == null
                || operation.getTrustAnchorChallengeExpiresAt() == null) {
            throw new IllegalArgumentException("Trust-anchor challenge is unavailable.");
        }

        PublicKey publicKey = DeviceTrustProtocol.decodeP256(request.publicKey());
        String fingerprint = DeviceTrustProtocol.fingerprint(publicKey);
        byte[] message = DeviceTrustProtocol.registrationMessage(
                deviceKeyFromClientId(expectedClientId), operationId,
                operation.getOwnerEmail().trim().toLowerCase(), expectedClientId,
                operation.getTrustAnchorChallenge(), fingerprint);
        verifySignature(publicKey, request.signature(), message);

        Instant now = Instant.now(clock);
        Query query = new Query(new Criteria().andOperator(
                baseCriteria(operation, ownerEmail),
                Criteria.where("trustAnchorChallenge").is(operation.getTrustAnchorChallenge()),
                Criteria.where("trustAnchorChallengeExpiresAt").gt(now),
                Criteria.where("trustAnchorFingerprint").exists(false)));
        Update update = new Update()
                .set("trustAnchorPublicKey", request.publicKey())
                .set("trustAnchorFingerprint", fingerprint)
                .set("trustAnchorRegisteredAt", now)
                .unset("trustAnchorChallenge")
                .unset("trustAnchorChallengeExpiresAt")
                .inc("version", 1)
                .set("updatedAt", now);
        DeviceEnrollmentOperation updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                DeviceEnrollmentOperation.class);
        if (updated == null) {
            throw new IllegalArgumentException("Trust-anchor challenge is expired, used, or already registered.");
        }
        return new TrustAnchorRegistrationResponse(
                updated.getId(), updated.getTrustAnchorFingerprint(),
                updated.getTrustAnchorRegisteredAt(), DeviceTrustProtocol.VERSION);
    }

    private DeviceEnrollmentOperation ownedOperation(String deviceId, String operationId,
            String ownerEmail) {
        if (deviceId == null || deviceId.isBlank() || operationId == null || operationId.isBlank()
                || ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Enrollment identity is required.");
        }
        return operationRepository.findById(operationId)
                .filter(operation -> deviceId.equals(operation.getDeviceId()))
                .filter(operation -> ownerEmail.trim().equalsIgnoreCase(operation.getOwnerEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Enrollment operation not found."));
    }

    private void requireReady(DeviceEnrollmentOperation operation) {
        if (operation.getState() != EnrollmentState.CREDENTIAL_DELIVERY_PENDING
                || !operation.isBrokerIdentityVerified()
                || operation.getBrokerClientId() == null
                || operation.getBrokerClientId().isBlank()
                || operation.getPairingSessionId() == null
                || operation.getPairingSessionId().isBlank()) {
            throw new IllegalStateException("Enrollment is not ready for trust-anchor registration.");
        }
    }

    private Criteria baseCriteria(DeviceEnrollmentOperation operation, String ownerEmail) {
        return new Criteria().andOperator(
                Criteria.where("_id").is(operation.getId()),
                Criteria.where("deviceId").is(operation.getDeviceId()),
                Criteria.where("ownerEmail").is(ownerEmail.trim().toLowerCase()),
                Criteria.where("state").is(EnrollmentState.CREDENTIAL_DELIVERY_PENDING),
                Criteria.where("brokerIdentityVerified").is(true),
                Criteria.where("version").is(operation.getVersion()));
    }

    private TrustAnchorChallengeResponse response(DeviceEnrollmentOperation operation,
            String challenge, Instant expiresAt) {
        return new TrustAnchorChallengeResponse(
                operation.getId(), operation.getDeviceId(),
                operation.getBrokerClientId(), challenge, expiresAt,
                DeviceTrustProtocol.VERSION);
    }

    private String randomChallenge() {
        byte[] value = new byte[32];
        ThreadLocalRandom.current().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void verifySignature(PublicKey publicKey, String encodedSignature, byte[] message) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update(message);
            if (!signature.verify(Base64.getDecoder().decode(encodedSignature))) {
                throw new IllegalArgumentException("Trust-anchor proof is invalid.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Trust-anchor proof is invalid.", exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Trust-anchor proof could not be verified.", exception);
        }
    }

    @SuppressWarnings("unused")
    private String deviceKeyFromOperation(DeviceEnrollmentOperation operation) {
        return deviceKeyFromClientId(operation.getBrokerClientId());
    }

    private String deviceKeyFromClientId(String clientId) {
        String prefix = "nexaiot-device-";
        if (clientId == null || !clientId.startsWith(prefix)
                || clientId.length() == prefix.length()) {
            throw new IllegalArgumentException("Enrollment client identity is invalid.");
        }
        return clientId.substring(prefix.length());
    }
}
