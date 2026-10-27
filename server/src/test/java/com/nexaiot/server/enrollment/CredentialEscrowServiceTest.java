package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

@ExtendWith(MockitoExtension.class)
class CredentialEscrowServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String OWNER = "owner@example.com";
    private static final String CLIENT = "nexaiot-device-NEXA-1";

    @Mock
    private DeviceEnrollmentOperationRepository operationRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    private CredentialEscrowKeyProvider keyProvider;
    private CredentialEscrowService service;
    private DeviceEnrollmentOperation operation;

    @BeforeEach
    void setUp() {
        keyProvider = new CredentialEscrowKeyProvider(
                Base64.getEncoder().encodeToString(new byte[32]), "key-1");
        service = new CredentialEscrowService(operationRepository, mongoTemplate, keyProvider,
                Duration.ofHours(1), Clock.fixed(NOW, ZoneOffset.UTC));
        operation = operation();
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation));
    }

    @Test
    void duplicateAndConcurrentEscrowCreationAreRejected() {
        DeviceEnrollmentOperation saved = operation();
        saved.setCredentialEscrowCiphertext("ciphertext");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(saved).thenReturn(null);
        GeneratedDeviceCredential credential = credential();

        service.escrow(operation, OWNER, credential);
        operation.setCredentialEscrowCiphertext("already-present");
        assertThrows(IllegalStateException.class, () -> service.escrow(operation, OWNER, credential));
    }

    @Test
    void persistenceFailureFailsClosedWithoutReturningCredential() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                org.mockito.ArgumentMatchers.<Class<DeviceEnrollmentOperation>>any()))
                .thenReturn(null);

        assertThrows(EnrollmentConcurrencyException.class,
                () -> service.escrow(operation, OWNER, credential()));
    }

    @Test
    void wrongOwnerOrCredentialIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.escrow(operation, "other@example.com", credential()));
        assertThrows(IllegalArgumentException.class,
                () -> service.escrow(operation, OWNER,
                        new GeneratedDeviceCredential("wrong", CLIENT, "pw")));
    }

    private DeviceEnrollmentOperation operation() {
        DeviceEnrollmentOperation value = new DeviceEnrollmentOperation();
        value.setId("operation-1");
        value.setDeviceId("device-1");
        value.setOwnerEmail(OWNER);
        value.setState(EnrollmentState.BROKER_VERIFIED);
        value.setVersion(0);
        value.setBrokerUsername(CLIENT);
        value.setBrokerClientId(CLIENT);
        return value;
    }

    private GeneratedDeviceCredential credential() {
        return new GeneratedDeviceCredential(CLIENT, CLIENT, "password-never-logged");
    }
}
