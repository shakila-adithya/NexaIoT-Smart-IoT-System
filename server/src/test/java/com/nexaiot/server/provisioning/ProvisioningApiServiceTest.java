package com.nexaiot.server.provisioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceProvisioningInfo;
import com.nexaiot.server.device.DeviceProvisioningStatus;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.enrollment.DeviceEnrollmentOperationRepository;
import com.nexaiot.server.enrollment.EnrollmentOperationService;
import com.nexaiot.server.provisioning.dto.PairingSessionResponse;
import com.nexaiot.server.provisioning.dto.ProvisioningMetadataResponse;

@ExtendWith(MockitoExtension.class)
class ProvisioningApiServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private ProvisioningSessionRepository sessionRepository;
    @Mock
    private PairingSessionService pairingSessionService;
    @Mock
    private EnrollmentOperationService enrollmentOperationService;
    @Mock
    private DeviceEnrollmentOperationRepository operationRepository;

    private ProvisioningApiService service;

    @BeforeEach
    void setUp() {
        service = new ProvisioningApiService(
                deviceRepository, sessionRepository, pairingSessionService,
                enrollmentOperationService, operationRepository);
    }

    @Test
    void metadataIsOwnerScopedAndDoesNotExposePairingSecret() {
        Device device = new Device();
        device.setId("device-1");
        device.setDeviceKey("NEXA-ABC");
        DeviceProvisioningInfo info = new DeviceProvisioningInfo();
        info.setStatus(DeviceProvisioningStatus.PROVISIONING);
        device.setProvisioningInfo(info);
        when(deviceRepository.findByIdAndOwnerEmail("device-1", "owner@example.com"))
                .thenReturn(Optional.of(device));

        ProvisioningSession active = session("session-1", "device-1", "owner@example.com");
        active.setTokenHash("never-return-this-hash");
        when(sessionRepository.findByDeviceIdAndOwnerEmailOrderByCreatedAtDesc(
                "device-1", "owner@example.com")).thenReturn(List.of(active));

        ProvisioningMetadataResponse response = service.metadata("device-1", "OWNER@EXAMPLE.COM");

        assertEquals("NEXA-ABC", response.deviceKey());
        assertEquals("PROVISIONING", response.status());
        assertEquals("session-1", response.activePairingSessionId());
    }

    @Test
    void sessionCannotBeReadThroughAnotherDeviceOrOwner() {
        when(sessionRepository.findById("session-1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getSession("other-device", "session-1", "owner@example.com"));
        verify(pairingSessionService, never()).getOwnerSession(anyString(), anyString());
    }

    @Test
    void claimDelegatesOnlyTheOneTimeTokenToExistingService() {
        when(pairingSessionService.claimPairingSession("session-1", "raw-token"))
                .thenReturn(new PairingClaimResult(
                        PairingClaimStatus.CLAIMED, "session-1", Instant.now().plusSeconds(60)));

        assertEquals("CLAIMED", service.claim("session-1", "raw-token").status());
        verify(pairingSessionService).claimPairingSession("session-1", "raw-token");
    }

    @Test
    void publicClaimResponseDoesNotEnumerateRejectedSessionState() {
        when(pairingSessionService.claimPairingSession("session-1", "raw-token"))
                .thenReturn(new PairingClaimResult(
                        PairingClaimStatus.EXPIRED, "session-1", Instant.now()));

        assertEquals("INVALID", service.claim("session-1", "raw-token").status());
    }

    private ProvisioningSession session(String id, String deviceId, String owner) {
        ProvisioningSession session = new ProvisioningSession();
        session.setId(id);
        session.setDeviceId(deviceId);
        session.setOwnerEmail(owner);
        session.setStatus(PairingSessionStatus.ACTIVE);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(600));
        session.setMaximumAttempts(5);
        return session;
    }
}
