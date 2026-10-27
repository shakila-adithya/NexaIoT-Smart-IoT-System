package com.nexaiot.server.enrollment;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.mqtt.management.BrokerDeviceIdentity;
import com.nexaiot.server.mqtt.management.BrokerDeviceProvisioningStatus;
import com.nexaiot.server.mqtt.management.BrokerManagementClient;
import com.nexaiot.server.mqtt.management.BrokerManagementException;
import com.nexaiot.server.mqtt.management.BrokerManagementFailure;
import com.nexaiot.server.mqtt.management.BrokerOperationResult;
import com.nexaiot.server.mqtt.management.DeviceCredentialGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceProvisioningOrchestratorTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private EnrollmentOperationService operationService;
    @Mock
    private BrokerManagementClient broker;
    @Mock
    private CredentialEscrowService credentialEscrowService;

    private DeviceProvisioningOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new DeviceProvisioningOrchestrator(
                deviceRepository, operationService, broker, new DeviceCredentialGenerator(),
                credentialEscrowService);
    }

    @Test
    void successfulProvisioningStopsBeforeCredentialDelivery() {
        DeviceEnrollmentOperation requested = operation(EnrollmentState.REQUESTED);
        DeviceEnrollmentOperation pending = operation(EnrollmentState.BROKER_CLIENT_PENDING);
        DeviceEnrollmentOperation created = operation(EnrollmentState.BROKER_CLIENT_CREATED);
        DeviceEnrollmentOperation aclPending = operation(EnrollmentState.ACL_PENDING);
        DeviceEnrollmentOperation aclAssigned = operation(EnrollmentState.ACL_ASSIGNED);
        DeviceEnrollmentOperation verified = operation(EnrollmentState.BROKER_VERIFIED);
        Device device = new Device();
        device.setId("device-1");
        device.setDeviceKey("NEXA-DEVICE-1");
        when(deviceRepository.findByIdAndOwnerEmail("device-1", "owner@example.com"))
                .thenReturn(Optional.of(device));
        when(operationService.transition("operation-1", EnrollmentState.REQUESTED,
                EnrollmentState.BROKER_CLIENT_PENDING, "owner@example.com")).thenReturn(pending);
        when(operationService.recordBrokerIdentity(eq("operation-1"), eq("owner@example.com"),
                anyString(), anyString(), eq("nexaiot-device-NEXA-DEVICE-1"), anyString()))
                .thenReturn(created);
        when(operationService.transition("operation-1", EnrollmentState.BROKER_CLIENT_CREATED,
                EnrollmentState.ACL_PENDING, "owner@example.com")).thenReturn(aclPending);
        when(operationService.transition("operation-1", EnrollmentState.ACL_PENDING,
                EnrollmentState.ACL_ASSIGNED, "owner@example.com")).thenReturn(aclAssigned);
        when(operationService.markBrokerVerified("operation-1", "owner@example.com"))
                .thenReturn(verified);
        when(credentialEscrowService.escrow(eq(verified), eq("owner@example.com"), any()))
                .thenReturn(verified);
        when(operationService.transition("operation-1", EnrollmentState.BROKER_VERIFIED,
                EnrollmentState.CREDENTIAL_DELIVERY_PENDING, "owner@example.com"))
                .thenReturn(operation(EnrollmentState.CREDENTIAL_DELIVERY_PENDING));
        when(broker.createDeviceIdentity(anyString(), anyString(), anyString()))
                .thenReturn(new BrokerDeviceIdentity("ignored", "ignored"));
        when(broker.assignDevicePermissions(anyString(), eq("NEXA-DEVICE-1")))
                .thenReturn(new BrokerOperationResult("assign", "NEXA-DEVICE-1", true));
        when(broker.verifyDeviceProvisioning(anyString(), eq("NEXA-DEVICE-1")))
                .thenReturn(new BrokerDeviceProvisioningStatus(
                        "nexaiot-device-NEXA-DEVICE-1", "nexaiot-device-NEXA-DEVICE-1",
                        "nexaiot-device-NEXA-DEVICE-1", 10, 10, true, true));

        DeviceEnrollmentOperation result = orchestrator.provision(requested, "owner@example.com");

        assertEquals(EnrollmentState.CREDENTIAL_DELIVERY_PENDING, result.getState());
        verify(broker).createDeviceIdentity(anyString(), anyString(), anyString());
        verify(broker).assignDevicePermissions(anyString(), eq("NEXA-DEVICE-1"));
        verify(broker).verifyDeviceProvisioning(anyString(), eq("NEXA-DEVICE-1"));
        verify(operationService, never()).markReconciliationRequired(
                anyString(), anyString(), any(), anyString());
    }

    @Test
    void brokerTimeoutFailsClosedIntoReconciliation() {
        DeviceEnrollmentOperation requested = operation(EnrollmentState.REQUESTED);
        DeviceEnrollmentOperation pending = operation(EnrollmentState.BROKER_CLIENT_PENDING);
        DeviceEnrollmentOperation reconciled = operation(EnrollmentState.RECONCILIATION_REQUIRED);
        Device device = new Device();
        device.setId("device-1");
        device.setDeviceKey("NEXA-DEVICE-1");
        when(deviceRepository.findByIdAndOwnerEmail("device-1", "owner@example.com"))
                .thenReturn(Optional.of(device));
        when(operationService.transition("operation-1", EnrollmentState.REQUESTED,
                EnrollmentState.BROKER_CLIENT_PENDING, "owner@example.com")).thenReturn(pending);
        when(broker.createDeviceIdentity(anyString(), anyString(), anyString()))
                .thenThrow(new BrokerManagementException(BrokerManagementFailure.TIMEOUT,
                        "broker timeout"));
        when(operationService.markReconciliationRequired("operation-1", "owner@example.com",
                EnrollmentState.BROKER_CLIENT_PENDING, "BROKER_TIMEOUT")).thenReturn(reconciled);

        DeviceEnrollmentOperation result = orchestrator.provision(requested, "owner@example.com");

        assertEquals(EnrollmentState.RECONCILIATION_REQUIRED, result.getState());
        verify(broker, never()).assignDevicePermissions(anyString(), anyString());
    }

    private DeviceEnrollmentOperation operation(EnrollmentState state) {
        DeviceEnrollmentOperation operation = new DeviceEnrollmentOperation();
        operation.setId("operation-1");
        operation.setDeviceId("device-1");
        operation.setOwnerEmail("owner@example.com");
        operation.setState(state);
        operation.setVersion(0);
        assertNotNull(operation.getId());
        return operation;
    }
}
