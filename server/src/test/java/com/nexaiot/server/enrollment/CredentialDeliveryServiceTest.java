package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

import com.nexaiot.server.enrollment.dto.CredentialDeliveryChallengeResponse;
import com.nexaiot.server.enrollment.dto.CredentialDeliveryRequest;
import com.nexaiot.server.enrollment.dto.CredentialDeliveryResponse;
import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

@ExtendWith(MockitoExtension.class)
class CredentialDeliveryServiceTest {

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

    private CredentialDeliveryService service;
    private CredentialEscrowKeyProvider keyProvider;
    private DeviceEnrollmentOperation operation;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = keyPair();
        keyProvider = new CredentialEscrowKeyProvider(
                Base64.getEncoder().encodeToString(new byte[32]), "key-1");
        service = new CredentialDeliveryService(operationRepository, mongoTemplate, keyProvider,
                Duration.ofMinutes(2), Clock.fixed(NOW, ZoneOffset.UTC));
        operation = operation();
        when(operationRepository.findById(OPERATION_ID)).thenReturn(Optional.of(operation));
    }

    @Test
    void authorizedDeliveryDecryptsAndConsumesEscrowAtomically() throws Exception {
        CredentialDeliveryChallengeResponse challenge = issueChallenge();
        DeviceEnrollmentOperation consumed = operation();
        consumed.setState(EnrollmentState.DEVICE_VERIFICATION_PENDING);
        consumed.setCredentialDelivered(true);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(consumed);

        CredentialDeliveryResponse response = service.deliver(
                DEVICE_ID, OPERATION_ID, request(challenge.challenge(), challenge.expiresAt()));

        assertEquals(CLIENT_ID, response.username());
        assertEquals(CLIENT_ID, response.mqttClientId());
        assertEquals("device-password", response.password());
    }

    @Test
    void forgedWrongIdentityAndExpiredProofAreRejected() throws Exception {
        CredentialDeliveryChallengeResponse challenge = issueChallenge();
        assertThrows(IllegalArgumentException.class, () -> service.deliver(
                DEVICE_ID, OPERATION_ID,
                new CredentialDeliveryRequest("other-client", challenge.challenge(),
                        sign(challenge.challenge(), challenge.expiresAt()))));

        operation.setCredentialDeliveryChallengeExpiresAt(NOW.minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.deliver(
                DEVICE_ID, OPERATION_ID, request("old", NOW.minusSeconds(1))));
    }

    @Test
    void replayOrPersistenceRaceDoesNotReturnCredentialTwice() throws Exception {
        CredentialDeliveryChallengeResponse challenge = issueChallenge();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(null);

        assertThrows(EnrollmentConcurrencyException.class, () -> service.deliver(
                DEVICE_ID, OPERATION_ID, request(challenge.challenge(), challenge.expiresAt())));
    }

    @Test
    void missingOrTamperedEscrowFailsClosed() throws Exception {
        operation.setCredentialEscrowCiphertext(null);
        assertThrows(IllegalStateException.class, () -> service.issueChallenge(
                DEVICE_ID, OPERATION_ID, OWNER));

        operation = operation();
        operation.setCredentialEscrowCiphertext(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }));
        when(operationRepository.findById(OPERATION_ID)).thenReturn(Optional.of(operation));
        CredentialDeliveryChallengeResponse challenge = issueChallenge();
        assertThrows(IllegalArgumentException.class, () -> service.deliver(
                DEVICE_ID, OPERATION_ID, request(challenge.challenge(), challenge.expiresAt())));
    }

    private CredentialDeliveryChallengeResponse issueChallenge() throws Exception {
        DeviceEnrollmentOperation challenged = operation();
        challenged.setCredentialDeliveryChallenge("delivery-challenge");
        challenged.setCredentialDeliveryChallengeExpiresAt(NOW.plusSeconds(120));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(challenged);
        CredentialDeliveryChallengeResponse response = service.issueChallenge(
                DEVICE_ID, OPERATION_ID, OWNER);
        operation.setCredentialDeliveryChallenge(response.challenge());
        operation.setCredentialDeliveryChallengeExpiresAt(response.expiresAt());
        return response;
    }

    private CredentialDeliveryRequest request(String challenge, Instant expiresAt) throws Exception {
        return new CredentialDeliveryRequest(CLIENT_ID, challenge, sign(challenge, expiresAt));
    }

    private String sign(String challenge, Instant expiresAt) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(DeviceTrustProtocol.deliveryMessage(
                DEVICE_KEY, OPERATION_ID, OWNER, CLIENT_ID,
                operation.getTrustAnchorFingerprint(), challenge, expiresAt.toString()));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private DeviceEnrollmentOperation operation() throws Exception {
        DeviceEnrollmentOperation value = new DeviceEnrollmentOperation();
        value.setId(OPERATION_ID);
        value.setDeviceId(DEVICE_ID);
        value.setOwnerEmail(OWNER);
        value.setState(EnrollmentState.CREDENTIAL_DELIVERY_PENDING);
        value.setVersion(0);
        value.setBrokerUsername(CLIENT_ID);
        value.setBrokerClientId(CLIENT_ID);
        value.setCredentialEscrowNonce(
                CredentialEscrowCipher.encrypt(keyProvider.currentKey(), "key-1", OPERATION_ID,
                        DEVICE_ID, new GeneratedDeviceCredential(CLIENT_ID, CLIENT_ID,
                                "device-password")).nonce());
        CredentialEscrowCipher.EncryptedCredential encrypted = CredentialEscrowCipher.encrypt(
                keyProvider.currentKey(), "key-1", OPERATION_ID, DEVICE_ID,
                new GeneratedDeviceCredential(CLIENT_ID, CLIENT_ID, "device-password"));
        value.setCredentialEscrowNonce(encrypted.nonce());
        value.setCredentialEscrowCiphertext(encrypted.ciphertext());
        value.setCredentialEscrowKeyVersion("key-1");
        value.setCredentialEscrowExpiresAt(NOW.plus(Duration.ofHours(1)));
        value.setTrustAnchorPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        value.setTrustAnchorFingerprint(DeviceTrustProtocol.fingerprint(keyPair.getPublic()));
        return value;
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
