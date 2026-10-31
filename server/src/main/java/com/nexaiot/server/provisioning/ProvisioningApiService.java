package com.nexaiot.server.provisioning;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceProvisioningInfo;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.enrollment.DeviceEnrollmentOperation;
import com.nexaiot.server.enrollment.DeviceEnrollmentOperationRepository;
import com.nexaiot.server.enrollment.EnrollmentOperationService;
import com.nexaiot.server.enrollment.DeviceProvisioningOrchestrator;
import com.nexaiot.server.enrollment.EnrollmentRecoveryService;
import com.nexaiot.server.provisioning.dto.EnrollmentOperationResponse;
import com.nexaiot.server.provisioning.dto.PairingClaimResponse;
import com.nexaiot.server.provisioning.dto.PairingSessionResponse;
import com.nexaiot.server.provisioning.dto.PairingSessionStartResponse;
import com.nexaiot.server.provisioning.dto.ProvisioningMetadataResponse;

@Service
public class ProvisioningApiService {

    private final DeviceRepository deviceRepository;
    private final ProvisioningSessionRepository sessionRepository;
    private final PairingSessionService pairingSessionService;
    private final EnrollmentOperationService enrollmentOperationService;
    private final DeviceEnrollmentOperationRepository operationRepository;
    private final DeviceProvisioningOrchestrator provisioningOrchestrator;
    private final EnrollmentRecoveryService recoveryService;

    @Autowired
    public ProvisioningApiService(
            DeviceRepository deviceRepository,
            ProvisioningSessionRepository sessionRepository,
            PairingSessionService pairingSessionService,
            EnrollmentOperationService enrollmentOperationService,
            DeviceEnrollmentOperationRepository operationRepository,
            DeviceProvisioningOrchestrator provisioningOrchestrator,
            EnrollmentRecoveryService recoveryService
    ) {
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.pairingSessionService = pairingSessionService;
        this.enrollmentOperationService = enrollmentOperationService;
        this.operationRepository = operationRepository;
        this.provisioningOrchestrator = provisioningOrchestrator;
        this.recoveryService = recoveryService;
    }

    /** Compatibility constructor for existing service-focused tests. */
    public ProvisioningApiService(
            DeviceRepository deviceRepository,
            ProvisioningSessionRepository sessionRepository,
            PairingSessionService pairingSessionService,
            EnrollmentOperationService enrollmentOperationService,
            DeviceEnrollmentOperationRepository operationRepository
    ) {
        this(deviceRepository, sessionRepository, pairingSessionService,
                enrollmentOperationService, operationRepository, null, null);
    }

    public PairingSessionStartResponse start(String deviceId, String ownerEmail) {
        PairingSessionStart start = pairingSessionService.startPairingSession(deviceId, ownerEmail);
        return new PairingSessionStartResponse(
                start.sessionId(), start.rawToken(), start.expiresAt(), start.maximumAttempts());
    }

    public PairingClaimResponse claim(String sessionId, String pairingToken) {
        PairingClaimResult result = pairingSessionService.claimPairingSession(sessionId, pairingToken);
        return toPublicClaimResponse(result);
    }

    public PairingClaimResponse claim(
            String sessionId,
            String pairingToken,
            String hardwareIdentityHash
    ) {
        PairingClaimResult result = pairingSessionService.claimPairingSession(
                sessionId, pairingToken, hardwareIdentityHash);
        return toPublicClaimResponse(result);
    }

    private PairingClaimResponse toPublicClaimResponse(PairingClaimResult result) {
        String publicStatus = result.status() == PairingClaimStatus.CLAIMED
                ? PairingClaimStatus.CLAIMED.name()
                : PairingClaimStatus.INVALID.name();
        return new PairingClaimResponse(result.sessionId(), publicStatus,
                result.status() == PairingClaimStatus.CLAIMED ? result.expiresAt() : null);
    }

    public PairingSessionResponse getSession(String deviceId, String sessionId, String ownerEmail) {
        ProvisioningSession session = ownedSession(deviceId, sessionId, ownerEmail);
        return toSessionResponse(session);
    }

    public void cancel(String deviceId, String sessionId, String ownerEmail) {
        ownedSession(deviceId, sessionId, ownerEmail);
        pairingSessionService.cancelPairingSession(sessionId, ownerEmail);
    }

    public ProvisioningMetadataResponse metadata(String deviceId, String ownerEmail) {
        Device device = ownedDevice(deviceId, ownerEmail);
        DeviceProvisioningInfo info = device.getProvisioningInfo();
        ProvisioningSession active = sessionRepository
                .findByDeviceIdAndOwnerEmailOrderByCreatedAtDesc(deviceId, normalize(ownerEmail))
                .stream()
                .filter(session -> session.getStatus() == PairingSessionStatus.ACTIVE)
                .findFirst()
                .orElse(null);
        return new ProvisioningMetadataResponse(
                device.getId(),
                device.getDeviceKey(),
                info == null || info.getStatus() == null ? null : info.getStatus().name(),
                info == null ? null : info.getProvisionedAt(),
                info == null ? null : info.getLastProvisioningAttemptAt(),
                active == null ? null : active.getId(),
                active == null ? null : active.getExpiresAt());
    }

    public EnrollmentOperationResponse createOperation(
            String deviceId,
            String pairingSessionId,
            String ownerEmail
    ) {
        ownedSession(deviceId, pairingSessionId, ownerEmail);
        DeviceEnrollmentOperation operation = enrollmentOperationService.createForClaimedSession(
                pairingSessionId, ownerEmail);
        if (provisioningOrchestrator == null) {
            return toOperationResponse(operation);
        }
        return toOperationResponse(provisioningOrchestrator.provision(operation, ownerEmail));
    }

    public EnrollmentOperationResponse getOperation(
            String deviceId,
            String operationId,
            String ownerEmail
    ) {
        DeviceEnrollmentOperation operation = operationRepository.findById(operationId)
                .filter(candidate -> normalize(ownerEmail).equals(candidate.getOwnerEmail()))
                .filter(candidate -> Objects.equals(deviceId, candidate.getDeviceId()))
                .orElseThrow(() -> new IllegalArgumentException("Enrollment operation not found."));
        return toOperationResponse(operation);
    }

    public EnrollmentOperationResponse reconcileOperation(
            String deviceId,
            String operationId,
            String ownerEmail
    ) {
        if (recoveryService == null) {
            throw new IllegalStateException("Recovery service is unavailable.");
        }
        operationRepository.findById(operationId)
                .filter(candidate -> normalize(ownerEmail).equals(candidate.getOwnerEmail()))
                .filter(candidate -> Objects.equals(deviceId, candidate.getDeviceId()))
                .orElseThrow(() -> new IllegalArgumentException("Enrollment operation not found."));
        recoveryService.reconcile(operationId, ownerEmail);
        return getOperation(deviceId, operationId, ownerEmail);
    }

    private ProvisioningSession ownedSession(String deviceId, String sessionId, String ownerEmail) {
        ProvisioningSession session = sessionRepository.findById(sessionId)
                .filter(candidate -> normalize(ownerEmail).equals(candidate.getOwnerEmail()))
                .filter(candidate -> Objects.equals(deviceId, candidate.getDeviceId()))
                .orElseThrow(() -> new IllegalArgumentException("Pairing session not found."));
        return session;
    }

    private Device ownedDevice(String deviceId, String ownerEmail) {
        return deviceRepository.findByIdAndOwnerEmail(deviceId, normalize(ownerEmail))
                .orElseThrow(() -> new IllegalArgumentException("Device not found."));
    }

    private PairingSessionResponse toSessionResponse(ProvisioningSession session) {
        return new PairingSessionResponse(
                session.getId(), session.getDeviceId(), session.getStatus().name(),
                session.getCreatedAt(), session.getExpiresAt(), session.getConsumedAt(),
                session.getAttemptCount(), session.getMaximumAttempts(), session.getFailureReason());
    }

    private EnrollmentOperationResponse toOperationResponse(DeviceEnrollmentOperation operation) {
        return new EnrollmentOperationResponse(
                operation.getId(), operation.getDeviceId(), operation.getPairingSessionId(),
                operation.getEnrollmentAttemptId(), operation.getState().name(), operation.getVersion(),
                operation.getCreatedAt(), operation.getUpdatedAt(), operation.isReconciliationRequired(),
                operation.isBrokerIdentityVerified(), operation.isCredentialDelivered(),
                operation.isDeviceVerified(), operation.getLastRecoveryDisposition(),
                operation.getLastRecoveryReason(), isRetrySafe(operation),
                isManualActionRequired(operation));
    }

    private boolean isRetrySafe(DeviceEnrollmentOperation operation) {
        return "RETRYABLE".equals(operation.getLastRecoveryDisposition())
                || "SAFE_TO_CONTINUE".equals(operation.getLastRecoveryDisposition());
    }

    private boolean isManualActionRequired(DeviceEnrollmentOperation operation) {
        return "CREDENTIAL_REISSUANCE_REQUIRED".equals(operation.getLastRecoveryDisposition())
                || "RECONCILIATION_REQUIRED".equals(operation.getLastRecoveryDisposition())
                || "MANUAL_REVIEW_REQUIRED".equals(operation.getLastRecoveryDisposition())
                || "TERMINAL_FAILURE".equals(operation.getLastRecoveryDisposition());
    }

    private String normalize(String ownerEmail) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Authenticated owner is required.");
        }
        return ownerEmail.trim().toLowerCase();
    }
}
