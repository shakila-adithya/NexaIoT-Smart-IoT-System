package com.nexaiot.server.enrollment;

import java.util.List;

import org.springframework.stereotype.Service;

import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.mqtt.management.BrokerDeviceIdentityStatus;
import com.nexaiot.server.mqtt.management.BrokerDeviceProvisioningStatus;
import com.nexaiot.server.mqtt.management.BrokerManagementClient;
import com.nexaiot.server.mqtt.management.BrokerManagementException;
import com.nexaiot.server.mqtt.management.BrokerManagementFailure;
import com.nexaiot.server.mqtt.management.BrokerRoleStatus;

/** Read-only broker reconciliation for an owner-authorized operation. */
@Service
public class EnrollmentRecoveryService {

    private final DeviceRepository deviceRepository;
    private final DeviceEnrollmentOperationRepository operationRepository;
    private final EnrollmentOperationService operationService;
    private final EnrollmentReconciliationPlanner planner;
    private final BrokerManagementClient broker;

    public EnrollmentRecoveryService(
            DeviceRepository deviceRepository,
            DeviceEnrollmentOperationRepository operationRepository,
            EnrollmentOperationService operationService,
            EnrollmentReconciliationPlanner planner,
            BrokerManagementClient broker
    ) {
        this.deviceRepository = deviceRepository;
        this.operationRepository = operationRepository;
        this.operationService = operationService;
        this.planner = planner;
        this.broker = broker;
    }

    public RecoveryAssessment reconcile(String operationId, String ownerEmail) {
        String owner = normalize(ownerEmail);
        DeviceEnrollmentOperation operation = operationRepository.findById(operationId)
                .filter(candidate -> owner.equals(candidate.getOwnerEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Enrollment operation not found."));
        String deviceKey = deviceRepository.findByIdAndOwnerEmail(operation.getDeviceId(), owner)
                .map(device -> device.getDeviceKey())
                .filter(key -> key != null && !key.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Device not found."));

        RecoveryAssessment assessment;
        try {
            BrokerEnrollmentSnapshot snapshot = inspect(operation, deviceKey);
            ReconciliationPlan plan = planner.classify(operation, snapshot);
            assessment = toAssessment(plan);
        } catch (BrokerManagementException exception) {
            assessment = new RecoveryAssessment(
                    ReconciliationOutcome.MANUAL_REVIEW_REQUIRED,
                    "Broker inspection failed with " + exception.getFailure().name() + ".",
                    false, true);
        } catch (RuntimeException exception) {
            assessment = new RecoveryAssessment(
                    ReconciliationOutcome.MANUAL_REVIEW_REQUIRED,
                    "Broker inspection could not be completed safely.",
                    false, true);
        }

        operationService.recordRecoveryAssessment(
                operation.getId(), owner, operation.getVersion(), assessment);
        return assessment;
    }

    private BrokerEnrollmentSnapshot inspect(
            DeviceEnrollmentOperation operation,
            String deviceKey
    ) {
        String username = nonBlankOrDefault(
                operation.getBrokerUsername(), "nexaiot-device-" + deviceKey);
        String roleName = nonBlankOrDefault(
                operation.getBrokerRoleName(), "nexaiot-device-" + deviceKey);
        boolean hasRecordedIdentity = hasText(operation.getBrokerUsername())
                && hasText(operation.getBrokerClientId());

        BrokerDeviceIdentityStatus client = inspectClient(username);
        BrokerRoleStatus role = inspectRole(roleName);
        boolean roleExists = role != null;
        boolean roleOwnershipMatches = roleExists && hasRecordedIdentity
                && role.referencesKnown()
                && role.referencedClients().equals(List.of(username))
                && role.referencedGroups().isEmpty();
        if (client == null) {
            return new BrokerEnrollmentSnapshot(false, false, roleExists, false,
                    operation.isCredentialDelivered(), roleOwnershipMatches, true, roleExists);
        }

        boolean identityMatches = hasRecordedIdentity
                && username.equals(client.username())
                && operation.getBrokerClientId().equals(client.clientId())
                && !client.disabled();
        boolean aclComplete = false;
        if (identityMatches && roleOwnershipMatches) {
            BrokerDeviceProvisioningStatus verified = broker.verifyDeviceProvisioning(
                    username, deviceKey);
            aclComplete = verified != null && verified.verified()
                    && operation.getBrokerClientId().equals(verified.clientId())
                    && roleName.equals(verified.roleName())
                    && verified.expectedAclCount() == verified.verifiedAclCount()
                    && verified.noUnexpectedAllowRules();
        }
        boolean unexpected = !identityMatches || (roleExists && !roleOwnershipMatches);
        return new BrokerEnrollmentSnapshot(true, identityMatches, roleExists, aclComplete,
                operation.isCredentialDelivered(), roleOwnershipMatches, true, unexpected);
    }

    private BrokerDeviceIdentityStatus inspectClient(String username) {
        try {
            return broker.inspectEnrollmentStatus(username);
        } catch (BrokerManagementException exception) {
            if (exception.getFailure() == BrokerManagementFailure.RESOURCE_NOT_FOUND) {
                return null;
            }
            throw exception;
        }
    }

    private BrokerRoleStatus inspectRole(String roleName) {
        try {
            return broker.inspectRole(roleName);
        } catch (BrokerManagementException exception) {
            if (exception.getFailure() == BrokerManagementFailure.RESOURCE_NOT_FOUND) {
                return null;
            }
            throw exception;
        }
    }

    private RecoveryAssessment toAssessment(ReconciliationPlan plan) {
        return switch (plan.outcome()) {
            case CONSISTENT, SAFE_TO_CONTINUE ->
                    new RecoveryAssessment(plan.outcome(), plan.reason(), true, false);
            case RETRYABLE -> new RecoveryAssessment(
                    plan.outcome(), plan.reason(), true, false);
            case CREDENTIAL_REISSUANCE_REQUIRED, RECONCILIATION_REQUIRED,
                    MANUAL_REVIEW_REQUIRED, TERMINAL_FAILURE -> new RecoveryAssessment(
                    plan.outcome(), plan.reason(), false, true);
        };
    }

    private String nonBlankOrDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String ownerEmail) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Authenticated owner is required.");
        }
        return ownerEmail.trim().toLowerCase();
    }
}
