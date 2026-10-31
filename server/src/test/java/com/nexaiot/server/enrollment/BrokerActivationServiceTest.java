package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.MessageChannel;

import com.nexaiot.server.mqtt.MqttTopicSecurity;

@ExtendWith(MockitoExtension.class)
class BrokerActivationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String OWNER = "owner@example.com";
    private static final String DEVICE_ID = "device-1";
    private static final String DEVICE_KEY = "NEXA-1";
    private static final String OPERATION_ID = "operation-1";
    private static final String CLIENT_ID = "nexaiot-device-" + DEVICE_KEY;

    @Mock
    private DeviceEnrollmentOperationRepository operationRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private MessageChannel outboundChannel;

    private BrokerActivationService service;
    private DeviceEnrollmentOperation operation;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = keyPair();
        service = new BrokerActivationService(operationRepository, mongoTemplate, outboundChannel,
                Duration.ofMinutes(2), Clock.fixed(NOW, ZoneOffset.UTC));
        operation = operation();
        lenient().when(operationRepository.findById(OPERATION_ID)).thenReturn(Optional.of(operation));
        lenient().when(outboundChannel.send(any())).thenReturn(true);
    }

    @Test
    void validFreshResponseCompletesEnrollmentOnce() throws Exception {
        issueChallenge();
        DeviceEnrollmentOperation completed = operation();
        completed.setState(EnrollmentState.COMPLETED);
        completed.setDeviceVerified(true);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(completed);

        assertTrue(service.consumeResponse(topic(), response(), false));
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class),
                org.mockito.ArgumentMatchers.eq("devices"));
    }

    @Test
    void retainedForgedExpiredAndWrongTopicResponsesAreRejected() throws Exception {
        issueChallenge();
        assertFalse(service.consumeResponse(topic(), response(), true));
        assertFalse(service.consumeResponse("nexaiot/devices/OTHER/unknown", response(), false));
        assertFalse(service.consumeResponse(topic(), forgedResponse(), false));

        operation.setActivationChallengeExpiresAt(NOW.minusSeconds(1));
        assertFalse(service.consumeResponse(topic(), response(), false));
    }

    @Test
    void persistenceRaceMakesDuplicateCompletionFailClosed() throws Exception {
        issueChallenge();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(null);

        assertFalse(service.consumeResponse(topic(), response(), false));
    }

    @Test
    void challengeRequiresOwnerAndEligibleEnrollment() {
        assertThrows(IllegalArgumentException.class,
                () -> service.issueChallenge(DEVICE_ID, OPERATION_ID, "other@example.com"));

        operation.setState(EnrollmentState.CREDENTIAL_DELIVERY_PENDING);
        assertThrows(IllegalStateException.class,
                () -> service.issueChallenge(DEVICE_ID, OPERATION_ID, OWNER));
    }

    private void issueChallenge() {
        DeviceEnrollmentOperation challenged = operation();
        challenged.setActivationChallenge("activation-challenge");
        challenged.setActivationChallengeExpiresAt(NOW.plusSeconds(120));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(challenged);
        service.issueChallenge(DEVICE_ID, OPERATION_ID, OWNER);
        operation.setActivationChallenge("activation-challenge");
        operation.setActivationChallengeExpiresAt(NOW.plusSeconds(120));
    }

    private byte[] response() throws Exception {
        return json(signature()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] forgedResponse() {
        return json("bad-signature").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String signature() throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(DeviceTrustProtocol.activationMessage(
                DEVICE_KEY, OPERATION_ID, OWNER, CLIENT_ID,
                operation.getTrustAnchorFingerprint(), operation.getActivationChallenge(),
                operation.getActivationChallengeExpiresAt().toString()));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private String json(String signature) {
        return "{\"operationId\":\"" + OPERATION_ID + "\","
                + "\"mqttClientId\":\"" + CLIENT_ID + "\","
                + "\"challenge\":\"activation-challenge\","
                + "\"expiresAt\":\"" + NOW.plusSeconds(120) + "\","
                + "\"signature\":\"" + signature + "\"}";
    }

    private String topic() {
        return MqttTopicSecurity.activationResponseTopic(DEVICE_KEY);
    }

    private DeviceEnrollmentOperation operation() {
        DeviceEnrollmentOperation value = new DeviceEnrollmentOperation();
        value.setId(OPERATION_ID);
        value.setDeviceId(DEVICE_ID);
        value.setOwnerEmail(OWNER);
        value.setState(EnrollmentState.DEVICE_VERIFICATION_PENDING);
        value.setVersion(0);
        value.setCredentialDelivered(true);
        value.setBrokerUsername(CLIENT_ID);
        value.setBrokerClientId(CLIENT_ID);
        value.setTrustAnchorPublicKey(
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        value.setTrustAnchorFingerprint(DeviceTrustProtocol.fingerprint(keyPair.getPublic()));
        return value;
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
