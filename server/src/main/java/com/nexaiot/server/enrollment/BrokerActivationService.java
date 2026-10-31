package com.nexaiot.server.enrollment;

import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexaiot.server.mqtt.MqttTopicSecurity;
import com.nexaiot.server.device.DeviceProvisioningStatus;

/** Broker-mediated activation using ACL authorization plus device key proof. */
@Service
public class BrokerActivationService {

    private final DeviceEnrollmentOperationRepository operationRepository;
    private final MongoTemplate mongoTemplate;
    private final MessageChannel outboundChannel;
    private final Duration challengeLifetime;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public BrokerActivationService(
            DeviceEnrollmentOperationRepository operationRepository,
            MongoTemplate mongoTemplate,
            @Qualifier("mqttOutboundChannel") MessageChannel outboundChannel,
            @org.springframework.beans.factory.annotation.Value(
                    "${provisioning.activation.challenge-lifetime-seconds:120}") long lifetimeSeconds
    ) {
        this(operationRepository, mongoTemplate, outboundChannel,
                Duration.ofSeconds(lifetimeSeconds), Clock.systemUTC());
    }

    BrokerActivationService(DeviceEnrollmentOperationRepository operationRepository,
            MongoTemplate mongoTemplate, MessageChannel outboundChannel,
            Duration challengeLifetime, Clock clock) {
        if (operationRepository == null || mongoTemplate == null || outboundChannel == null
                || challengeLifetime == null || challengeLifetime.isZero()
                || challengeLifetime.isNegative() || clock == null) {
            throw new IllegalArgumentException("Activation policy is invalid.");
        }
        this.operationRepository = operationRepository;
        this.mongoTemplate = mongoTemplate;
        this.outboundChannel = outboundChannel;
        this.challengeLifetime = challengeLifetime;
        this.clock = clock;
    }

    public String issueChallenge(String deviceId, String operationId, String ownerEmail) {
        DeviceEnrollmentOperation operation = owned(deviceId, operationId, ownerEmail);
        Instant now = Instant.now(clock);
        requirePending(operation, now);
        if (operation.getActivationChallenge() != null
                && operation.getActivationChallengeExpiresAt() != null
                && now.isBefore(operation.getActivationChallengeExpiresAt())) {
            publish(operation, operation.getActivationChallenge(),
                    operation.getActivationChallengeExpiresAt());
            return operation.getActivationChallenge();
        }
        String challenge = randomChallenge();
        Instant expiresAt = now.plus(challengeLifetime).truncatedTo(ChronoUnit.MILLIS);
        Query query = baseQuery(operation, ownerEmail, now, new Criteria().orOperator(
                        Criteria.where("activationChallenge").exists(false),
                        Criteria.where("activationChallengeExpiresAt").lte(now)));
        Update update = new Update()
                .set("activationChallenge", challenge)
                .set("activationChallengeExpiresAt", expiresAt)
                .set("updatedAt", now)
                .inc("version", 1);
        DeviceEnrollmentOperation updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                DeviceEnrollmentOperation.class);
        if (updated == null) {
            throw new EnrollmentConcurrencyException("Activation challenge changed concurrently.");
        }
        publish(updated, challenge, expiresAt);
        return challenge;
    }

    public boolean consumeResponse(String topic, byte[] payload, boolean retained) {
        if (retained || payload == null || payload.length == 0 || payload.length > 4096) {
            return false;
        }
        try {
            String deviceKey = parseResponseDeviceKey(topic);
            JsonNode node = mapper.readTree(payload);
            if (node == null || !node.isObject()
                    || !node.path("operationId").isTextual()
                    || !node.path("mqttClientId").isTextual()
                    || !node.path("challenge").isTextual()
                    || !node.path("expiresAt").isTextual()
                    || !node.path("signature").isTextual()) {
                return false;
            }
            DeviceEnrollmentOperation operation = operationRepository.findById(
                            node.path("operationId").asText())
                    .orElse(null);
            if (operation == null || !deviceKey(operation.getBrokerClientId()).equals(deviceKey)) {
                return false;
            }
            Instant now = Instant.now(clock);
            if (operation.getState() != EnrollmentState.DEVICE_VERIFICATION_PENDING
                    || operation.isDeviceVerified()
                    || operation.getActivationChallenge() == null
                    || !operation.getActivationChallenge().equals(node.path("challenge").asText())
                    || operation.getActivationChallengeExpiresAt() == null
                    || !now.isBefore(operation.getActivationChallengeExpiresAt())
                    || !operation.getActivationChallengeExpiresAt().toString()
                            .equals(node.path("expiresAt").asText())
                    || !operation.getBrokerClientId().equals(node.path("mqttClientId").asText())) {
                return false;
            }
            PublicKey publicKey = DeviceTrustProtocol.decodeP256(operation.getTrustAnchorPublicKey());
            if (!DeviceTrustProtocol.fingerprint(publicKey)
                    .equals(operation.getTrustAnchorFingerprint())) {
                return false;
            }
            verify(publicKey, node.path("signature").asText(), DeviceTrustProtocol.activationMessage(
                    deviceKey, operation.getId(), operation.getOwnerEmail(),
                    operation.getBrokerClientId(), operation.getTrustAnchorFingerprint(),
                    operation.getActivationChallenge(),
                    operation.getActivationChallengeExpiresAt().toString()));
            Query query = baseQuery(operation, operation.getOwnerEmail(), now,
                    Criteria.where("activationChallenge").is(operation.getActivationChallenge()),
                    Criteria.where("activationChallengeExpiresAt").gt(now),
                    Criteria.where("deviceVerified").is(false));
            Update update = new Update()
                    .set("state", EnrollmentState.COMPLETED)
                    .set("deviceVerified", true)
                    .set("completedAt", now)
                    .unset("activationChallenge")
                    .unset("activationChallengeExpiresAt")
                    .set("updatedAt", now)
                    .unset("activeOperationKey")
                    .inc("version", 1);
            DeviceEnrollmentOperation completed = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true),
                    DeviceEnrollmentOperation.class);
            if (completed == null) {
                return false;
            }
            Query deviceQuery = Query.query(new Criteria().andOperator(
                    Criteria.where("_id").is(completed.getDeviceId()),
                    Criteria.where("ownerEmail").is(completed.getOwnerEmail())));
            Update deviceUpdate = new Update()
                    .set("provisioningInfo.status", DeviceProvisioningStatus.PROVISIONED)
                    .set("provisioningInfo.provisionedAt", now)
                    .unset("provisioningInfo.failureReason")
                    .set("updatedAt", now);
            mongoTemplate.updateFirst(deviceQuery, deviceUpdate, "devices");
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void publish(DeviceEnrollmentOperation operation, String challenge, Instant expiresAt) {
        ObjectNode message = mapper.createObjectNode();
        message.put("protocol", "NexaIoT/Activation/v1");
        message.put("operationId", operation.getId());
        message.put("deviceKey", deviceKey(operation.getBrokerClientId()));
        message.put("mqttClientId", operation.getBrokerClientId());
        message.put("trustAnchorFingerprint", operation.getTrustAnchorFingerprint());
        message.put("challenge", challenge);
        message.put("expiresAt", expiresAt.toString());
        Message<String> outbound = MessageBuilder.withPayload(message.toString())
                .setHeader(MqttHeaders.TOPIC,
                        MqttTopicSecurity.activationChallengeTopic(
                                deviceKey(operation.getBrokerClientId())))
                .setHeader(MqttHeaders.QOS, 1)
                .build();
        if (!outboundChannel.send(outbound)) {
            throw new IllegalStateException("Activation challenge channel rejected the message.");
        }
    }

    private DeviceEnrollmentOperation owned(String deviceId, String operationId, String ownerEmail) {
        return operationRepository.findById(operationId)
                .filter(operation -> deviceId.equals(operation.getDeviceId()))
                .filter(operation -> ownerEmail.trim().equalsIgnoreCase(operation.getOwnerEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Enrollment operation not found."));
    }

    private void requirePending(DeviceEnrollmentOperation operation, Instant now) {
        if (operation.getState() != EnrollmentState.DEVICE_VERIFICATION_PENDING
                || !operation.isCredentialDelivered()
                || operation.isDeviceVerified()
                || operation.getTrustAnchorPublicKey() == null
                || operation.getTrustAnchorFingerprint() == null
                || operation.getBrokerClientId() == null
                || operation.getBrokerClientId().isBlank()) {
            throw new IllegalStateException("Enrollment is not ready for activation.");
        }
    }

    private Query baseQuery(DeviceEnrollmentOperation operation, String ownerEmail, Instant now,
            Criteria... additional) {
        List<Criteria> criteria = new ArrayList<>(List.of(
                Criteria.where("_id").is(operation.getId()),
                Criteria.where("deviceId").is(operation.getDeviceId()),
                Criteria.where("ownerEmail").is(ownerEmail.trim().toLowerCase()),
                Criteria.where("state").is(EnrollmentState.DEVICE_VERIFICATION_PENDING),
                Criteria.where("version").is(operation.getVersion()),
                Criteria.where("credentialDelivered").is(true),
                Criteria.where("deviceVerified").is(false)));
        if (additional != null) {
            criteria.addAll(List.of(additional));
        }
        return new Query(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));
    }

    private void verify(PublicKey key, String encodedSignature, byte[] message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(key);
        signature.update(message);
        if (!signature.verify(Base64.getDecoder().decode(encodedSignature))) {
            throw new IllegalArgumentException("Activation proof is invalid.");
        }
    }

    private String parseResponseDeviceKey(String topic) {
        String prefix = "nexaiot/devices/";
        String suffix = "/activation-responses";
        if (topic == null || !topic.startsWith(prefix) || !topic.endsWith(suffix)) {
            throw new IllegalArgumentException("Activation response topic is invalid.");
        }
        String deviceKey = topic.substring(prefix.length(), topic.length() - suffix.length());
        MqttTopicSecurity.validateDeviceKey(deviceKey);
        return deviceKey;
    }

    private String deviceKey(String clientId) {
        String prefix = "nexaiot-device-";
        if (clientId == null || !clientId.startsWith(prefix)
                || clientId.length() == prefix.length()) {
            throw new IllegalArgumentException("Enrollment client identity is invalid.");
        }
        return clientId.substring(prefix.length());
    }

    private String randomChallenge() {
        byte[] value = new byte[32];
        ThreadLocalRandom.current().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
