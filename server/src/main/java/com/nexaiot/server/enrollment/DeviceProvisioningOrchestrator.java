package com.nexaiot.server.enrollment;

import org.springframework.stereotype.Service;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.mqtt.management.BrokerManagementClient;
import com.nexaiot.server.mqtt.management.BrokerManagementException;
import com.nexaiot.server.mqtt.management.BrokerManagementFailure;
import com.nexaiot.server.mqtt.management.BrokerDeviceProvisioningStatus;
import com.nexaiot.server.mqtt.management.DeviceCredentialGenerator;
import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/**
 * Coordinates durable enrollment state with broker-side effects. MongoDB and
 * MQTT are not one transaction: every broker step is preceded by a durable
 * state transition, and uncertain outcomes fail closed into reconciliation.
 */
@Service
public class DeviceProvisioningOrchestrator {

    private final DeviceRepository deviceRepository;
    private final EnrollmentOperationService operationService;
    private final BrokerManagementClient broker;
    private final DeviceCredentialGenerator credentialGenerator;
    private final CredentialEscrowService credentialEscrowService;

    public DeviceProvisioningOrchestrator(
            DeviceRepository deviceRepository,
            EnrollmentOperationService operationService,
            BrokerManagementClient broker,
            DeviceCredentialGenerator credentialGenerator,
            CredentialEscrowService credentialEscrowService
    ) {
        this.deviceRepository = deviceRepository;
        this.operationService = operationService;
        this.broker = broker;
        this.credentialGenerator = credentialGenerator;
        this.credentialEscrowService = credentialEscrowService;
    }

    public DeviceEnrollmentOperation provision(
            DeviceEnrollmentOperation operation,
            String ownerEmail
    ) {
        if (operation == null) {
            throw new IllegalArgumentException("Enrollment operation is required.");
        }
        if (operation.getState() != EnrollmentState.REQUESTED) {
            return operation;
        }

        Device device = deviceRepository.findByIdAndOwnerEmail(
                        operation.getDeviceId(), normalize(ownerEmail))
                .orElseThrow(() -> new IllegalArgumentException("Device not found."));
        String deviceKey = device.getDeviceKey();
        if (deviceKey == null || deviceKey.isBlank()) {
            throw new IllegalStateException("Device identity is incomplete.");
        }

        DeviceEnrollmentOperation current = operationService.transition(
                operation.getId(), EnrollmentState.REQUESTED,
                EnrollmentState.BROKER_CLIENT_PENDING, ownerEmail);
        try {
            GeneratedDeviceCredential credential = credentialGenerator.generate(deviceKey);
            broker.createDeviceIdentity(
                    credential.username(), credential.clientId(), credential.password());
            current = operationService.recordBrokerIdentity(
                    current.getId(), ownerEmail, credential.username(), credential.clientId(),
                    roleName(deviceKey), ownershipMarker(current));

            current = operationService.transition(
                    current.getId(), EnrollmentState.BROKER_CLIENT_CREATED,
                    EnrollmentState.ACL_PENDING, ownerEmail);
            broker.assignDevicePermissions(credential.username(), deviceKey);

            current = operationService.transition(
                    current.getId(), EnrollmentState.ACL_PENDING,
                    EnrollmentState.ACL_ASSIGNED, ownerEmail);
            BrokerDeviceProvisioningStatus verified = broker.verifyDeviceProvisioning(
                    credential.username(), deviceKey);
            if (verified == null || !verified.verified()
                    || !credential.username().equals(verified.username())
                    || !credential.clientId().equals(verified.clientId())
                    || !roleName(deviceKey).equals(verified.roleName())
                    || verified.expectedAclCount() != verified.verifiedAclCount()
                    || !verified.noUnexpectedAllowRules()) {
                throw new BrokerManagementException(
                        BrokerManagementFailure.INVALID_RESPONSE,
                        "Broker provisioning verification was incomplete.");
            }

            current = operationService.markBrokerVerified(current.getId(), ownerEmail);
            current = credentialEscrowService.escrow(current, ownerEmail, credential);
            return operationService.transition(
                    current.getId(), EnrollmentState.BROKER_VERIFIED,
                    EnrollmentState.CREDENTIAL_DELIVERY_PENDING, ownerEmail);
        } catch (BrokerManagementException exception) {
            return recordBrokerFailure(current, ownerEmail, exception);
        } catch (RuntimeException exception) {
            return recordUnknownFailure(current, ownerEmail, "BROKER_ORCHESTRATION_FAILURE");
        }
    }

    private DeviceEnrollmentOperation recordBrokerFailure(
            DeviceEnrollmentOperation current,
            String ownerEmail,
            BrokerManagementException exception
    ) {
        String code = "BROKER_" + exception.getFailure().name();
        return operationService.markReconciliationRequired(
                current.getId(), ownerEmail, current.getState(), code);
    }

    private DeviceEnrollmentOperation recordUnknownFailure(
            DeviceEnrollmentOperation current,
            String ownerEmail,
            String code
    ) {
        return operationService.markReconciliationRequired(
                current.getId(), ownerEmail, current.getState(), code);
    }

    private String roleName(String deviceKey) {
        return "nexaiot-device-" + deviceKey;
    }

    private String ownershipMarker(DeviceEnrollmentOperation operation) {
        return "nexaiot-enrollment:" + operation.getId();
    }

    private String normalize(String ownerEmail) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Authenticated owner is required.");
        }
        return ownerEmail.trim().toLowerCase();
    }
}
