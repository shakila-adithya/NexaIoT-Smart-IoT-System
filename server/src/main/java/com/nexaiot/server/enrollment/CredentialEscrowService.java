package com.nexaiot.server.enrollment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/** Durable, non-releasing escrow for a generated device MQTT credential. */
@Service
public class CredentialEscrowService {

    private final DeviceEnrollmentOperationRepository operationRepository;
    private final MongoTemplate mongoTemplate;
    private final CredentialEscrowKeyProvider keyProvider;
    private final Duration lifetime;
    private final Clock clock;

    @Autowired
    public CredentialEscrowService(
            DeviceEnrollmentOperationRepository operationRepository,
            MongoTemplate mongoTemplate,
            CredentialEscrowKeyProvider keyProvider,
            @Value("${provisioning.credential-escrow.expiry-seconds:86400}") long lifetimeSeconds
    ) {
        this(operationRepository, mongoTemplate, keyProvider,
                Duration.ofSeconds(lifetimeSeconds), Clock.systemUTC());
    }

    CredentialEscrowService(DeviceEnrollmentOperationRepository operationRepository,
            MongoTemplate mongoTemplate, CredentialEscrowKeyProvider keyProvider,
            Duration lifetime, Clock clock) {
        if (operationRepository == null || mongoTemplate == null || keyProvider == null
                || lifetime == null || lifetime.isZero() || lifetime.isNegative() || clock == null) {
            throw new IllegalArgumentException("Credential escrow policy is invalid.");
        }
        this.operationRepository = operationRepository;
        this.mongoTemplate = mongoTemplate;
        this.keyProvider = keyProvider;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    public DeviceEnrollmentOperation escrow(DeviceEnrollmentOperation operation,
            String ownerEmail, GeneratedDeviceCredential credential) {
        if (operation == null || ownerEmail == null || ownerEmail.isBlank() || credential == null) {
            throw new IllegalArgumentException("Credential escrow request is invalid.");
        }
        DeviceEnrollmentOperation current = operationRepository.findById(operation.getId())
                .filter(candidate -> normalize(ownerEmail).equals(candidate.getOwnerEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Enrollment operation not found."));
        if (current.getState() != EnrollmentState.BROKER_VERIFIED
                || current.getCredentialEscrowCiphertext() != null) {
            throw new IllegalStateException("Enrollment is not eligible for credential escrow.");
        }
        String deviceKey = deviceKey(current.getBrokerClientId());
        String expectedIdentity = "nexaiot-device-" + deviceKey;
        if (!expectedIdentity.equals(credential.username())
                || !expectedIdentity.equals(credential.clientId())) {
            throw new IllegalArgumentException("Credential identity is not bound to enrollment.");
        }

        String keyVersion = keyProvider.currentKeyVersion();
        CredentialEscrowCipher.EncryptedCredential encrypted = CredentialEscrowCipher.encrypt(
                keyProvider.currentKey(), keyVersion, current.getId(), current.getDeviceId(), credential);
        Instant now = Instant.now(clock);
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(current.getId()),
                Criteria.where("ownerEmail").is(normalize(ownerEmail)),
                Criteria.where("deviceId").is(current.getDeviceId()),
                Criteria.where("state").is(EnrollmentState.BROKER_VERIFIED),
                Criteria.where("version").is(current.getVersion()),
                Criteria.where("credentialEscrowCiphertext").exists(false)));
        Update update = new Update()
                .set("credentialEscrowNonce", encrypted.nonce())
                .set("credentialEscrowCiphertext", encrypted.ciphertext())
                .set("credentialEscrowKeyVersion", encrypted.keyVersion())
                .set("credentialEscrowCreatedAt", now)
                .set("credentialEscrowExpiresAt", now.plus(lifetime))
                .set("credentialReference", "escrow:" + current.getId())
                .set("updatedAt", now)
                .inc("version", 1);
        DeviceEnrollmentOperation saved = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                DeviceEnrollmentOperation.class);
        if (saved == null) {
            throw new EnrollmentConcurrencyException(
                    "Credential escrow changed concurrently or already exists.");
        }
        return saved;
    }

    /** Explicit operator/recovery cleanup; it never changes broker credentials. */
    public DeviceEnrollmentOperation expire(DeviceEnrollmentOperation operation, String ownerEmail) {
        if (operation == null || ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Credential escrow cleanup request is invalid.");
        }
        Instant now = Instant.now(clock);
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(operation.getId()),
                Criteria.where("ownerEmail").is(normalize(ownerEmail)),
                Criteria.where("version").is(operation.getVersion()),
                Criteria.where("credentialEscrowExpiresAt").lte(now),
                Criteria.where("credentialEscrowCiphertext").exists(true)));
        Update update = new Update()
                .unset("credentialEscrowNonce")
                .unset("credentialEscrowCiphertext")
                .set("lastRecoveryDisposition", ReconciliationOutcome.CREDENTIAL_REISSUANCE_REQUIRED.name())
                .set("lastRecoveryReason", "Encrypted credential escrow expired; explicit recovery required.")
                .set("updatedAt", now)
                .inc("version", 1);
        DeviceEnrollmentOperation expired = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                DeviceEnrollmentOperation.class);
        if (expired == null) {
            throw new EnrollmentConcurrencyException("Credential escrow was not eligible for cleanup.");
        }
        return expired;
    }

    private String deviceKey(String clientId) {
        String prefix = "nexaiot-device-";
        if (clientId == null || !clientId.startsWith(prefix) || clientId.length() == prefix.length()) {
            throw new IllegalArgumentException("Enrollment client identity is invalid.");
        }
        return clientId.substring(prefix.length());
    }

    private String normalize(String ownerEmail) {
        return ownerEmail.trim().toLowerCase();
    }
}
