package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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

import com.nexaiot.server.enrollment.dto.TrustAnchorRegistrationRequest;
import com.nexaiot.server.enrollment.dto.TrustAnchorChallengeResponse;
import com.nexaiot.server.enrollment.dto.TrustAnchorRegistrationResponse;

@ExtendWith(MockitoExtension.class)
class DeviceTrustAnchorServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DEVICE_ID = "device-1";
    private static final String DEVICE_KEY = "NEXA-TRUST-1";
    private static final String OPERATION_ID = "operation-1";
    private static final String OWNER = "owner@example.com";
    private static final String CLIENT_ID = "nexaiot-device-" + DEVICE_KEY;

    @Mock
    private DeviceEnrollmentOperationRepository operationRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    private DeviceTrustAnchorService service;
    private DeviceEnrollmentOperation operation;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        service = new DeviceTrustAnchorService(operationRepository, mongoTemplate,
                Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));
        operation = operation();
        keyPair = keyPair();
        when(operationRepository.findById(OPERATION_ID)).thenReturn(Optional.of(operation));
    }

    @Test
    void validOwnerAuthorizedRegistrationStoresOnlyPublicMetadata() throws Exception {
        TrustAnchorChallengeResponse challenge = challenge();
        DeviceEnrollmentOperation registered = operation();
        registered.setTrustAnchorFingerprint(fingerprint());
        registered.setTrustAnchorRegisteredAt(NOW);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(registered);

        TrustAnchorRegistrationResponse response = service.register(
                DEVICE_ID, OPERATION_ID, OWNER, request(challenge.challenge()));

        assertEquals(fingerprint(), response.fingerprint());
        assertEquals(DeviceTrustProtocol.VERSION, response.protocol());
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        TrustAnchorChallengeResponse challenge = challenge();
        KeyPair other = keyPair();
        String signature = sign(other, challenge.challenge(), fingerprint());
        assertThrows(IllegalArgumentException.class, () -> service.register(
                DEVICE_ID, OPERATION_ID, OWNER,
                new TrustAnchorRegistrationRequest(CLIENT_ID, publicKey(), signature)));
    }

    @Test
    void wrongOwnerDeviceOperationAndClientAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service.issueChallenge(DEVICE_ID, OPERATION_ID, "other@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service.issueChallenge("device-other", OPERATION_ID, OWNER));
        challenge();
        assertThrows(IllegalArgumentException.class, () -> service.register(
                DEVICE_ID, OPERATION_ID, OWNER,
                new TrustAnchorRegistrationRequest("nexaiot-device-OTHER", publicKey(), "bad")));
    }

    @Test
    void expiredChallengeIsRejected() throws Exception {
        operation.setTrustAnchorChallenge("expired");
        operation.setTrustAnchorChallengeExpiresAt(NOW.minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.register(
                DEVICE_ID, OPERATION_ID, OWNER, request("expired")));
    }

    @Test
    void replayAndConcurrentSecondRegistrationAreRejected() throws Exception {
        TrustAnchorChallengeResponse challenge = challenge();
        DeviceEnrollmentOperation registered = operation();
        registered.setTrustAnchorFingerprint(fingerprint());
        registered.setTrustAnchorRegisteredAt(NOW);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(challengeOperation()).thenReturn(null);

        service.register(DEVICE_ID, OPERATION_ID, OWNER, request(challenge.challenge()));
        assertThrows(IllegalArgumentException.class, () -> service.register(
                DEVICE_ID, OPERATION_ID, OWNER, request(challenge.challenge())));
    }

    @Test
    void duplicateRegistrationIsRejectedBeforeVerification() throws Exception {
        operation.setTrustAnchorFingerprint("already-registered");
        challenge();
        assertThrows(IllegalArgumentException.class, () -> service.register(
                DEVICE_ID, OPERATION_ID, OWNER, request(operation.getTrustAnchorChallenge())));
    }

    private TrustAnchorChallengeResponse challenge() {
        DeviceEnrollmentOperation challenged = challengeOperation();
        lenient().when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(challenged);
        return service.issueChallenge(DEVICE_ID, OPERATION_ID, OWNER);
    }

    private DeviceEnrollmentOperation challengeOperation() {
        operation.setTrustAnchorChallenge("challenge-1");
        operation.setTrustAnchorChallengeExpiresAt(NOW.plusSeconds(300));
        return operation;
    }

    private TrustAnchorRegistrationRequest request(String challenge) throws Exception {
        return new TrustAnchorRegistrationRequest(CLIENT_ID, publicKey(),
                sign(keyPair, challenge, fingerprint()));
    }

    private String publicKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private String fingerprint() {
        return DeviceTrustProtocol.fingerprint(keyPair.getPublic());
    }

    private String sign(KeyPair signer, String challenge, String fingerprint) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(signer.getPrivate());
        signature.update(DeviceTrustProtocol.registrationMessage(
                DEVICE_KEY, OPERATION_ID, OWNER, CLIENT_ID, challenge, fingerprint));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private DeviceEnrollmentOperation operation() {
        DeviceEnrollmentOperation value = new DeviceEnrollmentOperation();
        value.setId(OPERATION_ID);
        value.setDeviceId(DEVICE_ID);
        value.setOwnerEmail(OWNER);
        value.setPairingSessionId("pairing-1");
        value.setBrokerClientId(CLIENT_ID);
        value.setState(EnrollmentState.CREDENTIAL_DELIVERY_PENDING);
        value.setBrokerIdentityVerified(true);
        value.setVersion(0);
        return value;
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
