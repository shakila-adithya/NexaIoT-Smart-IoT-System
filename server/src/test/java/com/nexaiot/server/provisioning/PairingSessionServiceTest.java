package com.nexaiot.server.provisioning;

import com.nexaiot.server.device.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PairingSessionServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private ProvisioningSessionRepository sessionRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    private PairingTokenService tokenService;
    private PairingSessionService service;

    @BeforeEach
    void setUp() {
        tokenService = new PairingTokenService();
        service = new PairingSessionService(
                deviceRepository, sessionRepository, tokenService, mongoTemplate, 600, 3);
    }

    @Test
    void wrongHardwareIdentityIsRejectedAndConsumesAnAttempt() {
        String token = tokenService.generateToken();
        ProvisioningSession session = activeSession(token, "expected-device-hash");
        ProvisioningSession updated = activeSession(token, "expected-device-hash");
        updated.setAttemptCount(1);
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ProvisioningSession.class))).thenReturn(updated);

        PairingClaimResult result = service.claimPairingSession(
                "session-1", token, "wrong-device-hash");

        assertEquals(PairingClaimStatus.INVALID, result.status());
        verify(mongoTemplate).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ProvisioningSession.class));
    }

    @Test
    void concurrentSecondClaimCannotConsumeAlreadyClaimedSession() {
        String token = tokenService.generateToken();
        ProvisioningSession session = activeSession(token, null);
        ProvisioningSession claimed = activeSession(token, null);
        claimed.setStatus(PairingSessionStatus.CLAIMED);
        AtomicInteger lookupCount = new AtomicInteger();
        when(sessionRepository.findById("session-1"))
                .thenAnswer(invocation -> lookupCount.getAndIncrement() == 0
                        ? Optional.of(session)
                        : Optional.of(claimed));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ProvisioningSession.class))).thenReturn(null);

        PairingClaimResult result = service.claimPairingSession("session-1", token);

        assertEquals(PairingClaimStatus.ALREADY_CLAIMED, result.status());
    }

    @Test
    void expiredSessionIsRejectedWithoutAttemptMutation() {
        String token = tokenService.generateToken();
        ProvisioningSession session = activeSession(token, null);
        session.setExpiresAt(Instant.now().minusSeconds(1));
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));

        PairingClaimResult result = service.claimPairingSession("session-1", token);

        assertEquals(PairingClaimStatus.EXPIRED, result.status());
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class),
                eq(ProvisioningSession.class));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ProvisioningSession.class));
    }

    private ProvisioningSession activeSession(String token, String identityHash) {
        ProvisioningSession session = new ProvisioningSession();
        session.setId("session-1");
        session.setDeviceId("device-1");
        session.setOwnerEmail("owner@example.com");
        session.setTokenHash(tokenService.hashToken(token));
        session.setStatus(PairingSessionStatus.ACTIVE);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(600));
        session.setMaximumAttempts(3);
        session.setAttemptCount(0);
        session.setHardwareIdentityHash(identityHash);
        return session;
    }
}
