package com.nexaiot.server.enrollment;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.mqtt.management.BrokerDeviceIdentityStatus;
import com.nexaiot.server.mqtt.management.BrokerDeviceProvisioningStatus;
import com.nexaiot.server.mqtt.management.BrokerManagementClient;
import com.nexaiot.server.mqtt.management.BrokerManagementException;
import com.nexaiot.server.mqtt.management.BrokerManagementFailure;
import com.nexaiot.server.mqtt.management.BrokerRoleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentRecoveryServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceEnrollmentOperationRepository operationRepository;
    @Mock
    private EnrollmentOperationService operationService;
    @Mock
    private BrokerManagementClient broker;

    private EnrollmentRecoveryService recovery;

    @BeforeEach
    void setUp() {
        recovery = new EnrollmentRecoveryService(
                deviceRepository, operationRepository, operationService,
                new EnrollmentReconciliationPlanner(), broker);
        Device device = new Device();
        device.setId("device-1");
        device.setDeviceKey("NEXA-DEVICE-1");
        when(deviceRepository.findByIdAndOwnerEmail("device-1", "owner@example.com"))
                .thenReturn(Optional.of(device));
    }

    @Test
    void missingClientAfterCrashIsRetryable() {
        DeviceEnrollmentOperation operation = operation(EnrollmentState.BROKER_CLIENT_PENDING);
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation));
        when(broker.inspectEnrollmentStatus("nexaiot-device-NEXA-DEVICE-1"))
                .thenThrow(new BrokerManagementException(
                        BrokerManagementFailure.RESOURCE_NOT_FOUND, "not found"));
        when(broker.inspectRole("nexaiot-device-NEXA-DEVICE-1"))
                .thenThrow(new BrokerManagementException(
                        BrokerManagementFailure.RESOURCE_NOT_FOUND, "not found"));

        RecoveryAssessment assessment = recovery.reconcile("operation-1", "owner@example.com");

        assertEquals(ReconciliationOutcome.RETRYABLE, assessment.outcome());
        verify(operationService).recordRecoveryAssessment(
                eq("operation-1"), eq("owner@example.com"), eq(0), any(RecoveryAssessment.class));
    }

    @Test
    void provisionedBrokerIdentityWithLostPasswordRequiresReissuance() {
        DeviceEnrollmentOperation operation = operation(EnrollmentState.CREDENTIAL_DELIVERY_PENDING);
        operation.setBrokerUsername("nexaiot-device-NEXA-DEVICE-1");
        operation.setBrokerClientId("nexaiot-device-NEXA-DEVICE-1");
        operation.setBrokerRoleName("nexaiot-device-NEXA-DEVICE-1");
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation));
        when(broker.inspectEnrollmentStatus("nexaiot-device-NEXA-DEVICE-1"))
                .thenReturn(new BrokerDeviceIdentityStatus(
                        "nexaiot-device-NEXA-DEVICE-1", "nexaiot-device-NEXA-DEVICE-1",
                        false, true));
        when(broker.inspectRole("nexaiot-device-NEXA-DEVICE-1"))
                .thenReturn(new BrokerRoleStatus(
                        "nexaiot-device-NEXA-DEVICE-1",
                        List.of("nexaiot-device-NEXA-DEVICE-1"), List.of(), true, true));
        when(broker.verifyDeviceProvisioning(
                "nexaiot-device-NEXA-DEVICE-1", "NEXA-DEVICE-1"))
                .thenReturn(new BrokerDeviceProvisioningStatus(
                        "nexaiot-device-NEXA-DEVICE-1", "nexaiot-device-NEXA-DEVICE-1",
                        "nexaiot-device-NEXA-DEVICE-1", 10, 10, true, true));

        RecoveryAssessment assessment = recovery.reconcile("operation-1", "owner@example.com");

        assertEquals(ReconciliationOutcome.CREDENTIAL_REISSUANCE_REQUIRED, assessment.outcome());
        verify(operationService).recordRecoveryAssessment(
                eq("operation-1"), eq("owner@example.com"), eq(0), any(RecoveryAssessment.class));
    }

    @Test
    void unexpectedRoleOwnershipRequiresManualReview() {
        DeviceEnrollmentOperation operation = operation(EnrollmentState.BROKER_CLIENT_CREATED);
        operation.setBrokerUsername("nexaiot-device-NEXA-DEVICE-1");
        operation.setBrokerClientId("nexaiot-device-NEXA-DEVICE-1");
        operation.setBrokerRoleName("nexaiot-device-NEXA-DEVICE-1");
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation));
        when(broker.inspectEnrollmentStatus("nexaiot-device-NEXA-DEVICE-1"))
                .thenReturn(new BrokerDeviceIdentityStatus(
                        "nexaiot-device-NEXA-DEVICE-1", "nexaiot-device-NEXA-DEVICE-1",
                        false, true));
        when(broker.inspectRole("nexaiot-device-NEXA-DEVICE-1"))
                .thenReturn(new BrokerRoleStatus(
                        "nexaiot-device-NEXA-DEVICE-1", List.of("unexpected-client"), List.of(), true, true));

        RecoveryAssessment assessment = recovery.reconcile("operation-1", "owner@example.com");

        assertEquals(ReconciliationOutcome.MANUAL_REVIEW_REQUIRED, assessment.outcome());
    }

    @Test
    void failedInspectionFailsClosed() {
        DeviceEnrollmentOperation operation = operation(EnrollmentState.RECONCILIATION_REQUIRED);
        when(operationRepository.findById("operation-1")).thenReturn(Optional.of(operation));
        when(broker.inspectEnrollmentStatus("nexaiot-device-NEXA-DEVICE-1"))
                .thenThrow(new BrokerManagementException(
                        BrokerManagementFailure.TIMEOUT, "timeout"));

        RecoveryAssessment assessment = recovery.reconcile("operation-1", "owner@example.com");

        assertEquals(ReconciliationOutcome.MANUAL_REVIEW_REQUIRED, assessment.outcome());
    }

    private DeviceEnrollmentOperation operation(EnrollmentState state) {
        DeviceEnrollmentOperation operation = new DeviceEnrollmentOperation();
        operation.setId("operation-1");
        operation.setDeviceId("device-1");
        operation.setOwnerEmail("owner@example.com");
        operation.setState(state);
        operation.setVersion(0);
        return operation;
    }
}
