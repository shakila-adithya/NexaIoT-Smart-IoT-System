package com.nexaiot.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceMqttCredentialInfo;
import com.nexaiot.server.enrollment.DeviceEnrollmentOperation;
import com.nexaiot.server.enrollment.EnrollmentState;
import com.nexaiot.server.exception.DeletionBlockedException;
import com.nexaiot.server.exception.InvalidCredentialsException;
import com.nexaiot.server.model.User;
import com.nexaiot.server.mqtt.management.*;
import com.nexaiot.server.repository.UserRepository;

class AccountDeletionServiceTest {
    private static final String IDENTITY = "nexaiot-device-NEXA-TEST";
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final BrokerManagementClient broker = mock(BrokerManagementClient.class);
    private final GridFsTemplate images = mock(GridFsTemplate.class);
    private final ObjectProvider<GridFsTemplate> gridFs = mockObjectProvider();
    private AccountDeletionService service;

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockObjectProvider() {
        return mock(ObjectProvider.class);
    }

    @BeforeEach
    void setUp() {
        when(gridFs.getIfAvailable()).thenReturn(images);
        service = new AccountDeletionService(users, encoder, mongo, gridFs, broker);
        when(mongo.find(any(Query.class), eq(DeviceEnrollmentOperation.class))).thenReturn(List.of());
        when(broker.inspectEnrollmentStatus(IDENTITY)).thenReturn(
                new BrokerDeviceIdentityStatus(IDENTITY, IDENTITY, false, true));
        when(broker.disableDeviceIdentity(IDENTITY)).thenReturn(result("disableDeviceIdentity"));
        when(broker.revokeDeviceIdentity(IDENTITY)).thenReturn(result("revokeDeviceIdentity"));
        when(broker.inspectRole(IDENTITY)).thenReturn(new BrokerRoleStatus(IDENTITY, List.of(), List.of(), true, true));
        when(broker.deleteRole(IDENTITY)).thenReturn(result("deleteRole"));
    }

    @Test
    void selfDeleteUsesCorrectResetTokenFieldsAndCleansEachCollectionOnce() {
        User user = prepareAccount();
        service.deleteSelf(user.getEmail(), "correct");

        verify(mongo).remove(query("deviceId", new Document("$in", List.of("device-1"))), eq("sensor_readings"));
        for (String collection : List.of("alerts", "alertRules", "maintenance_tasks", "generated_reports",
                "provisioning_sessions", "device_enrollment_operations")) {
            verify(mongo).remove(argThat(q -> q.getQueryObject().equals(new Document("$or", List.of(
                    new Document("ownerEmail", user.getEmail()),
                    new Document("deviceId", new Document("$in", List.of("device-1"))))))), eq(collection));
        }
        verify(mongo).remove(argThat(q -> q.getQueryObject().equals(new Document("$or", List.of(
                new Document("userId", user.getId()), new Document("userEmail", user.getEmail()))))),
                eq("password_reset_tokens"));
        verify(images).delete(any(Query.class));
        var order = inOrder(broker, mongo, users);
        order.verify(broker).revokeDeviceIdentity(IDENTITY);
        order.verify(mongo).remove(any(Query.class), eq("sensor_readings"));
        order.verify(mongo).remove(query("ownerEmail", user.getEmail()), eq("devices"));
        order.verify(users).delete(user);
    }

    @Test
    void deviceDeletionRevokesAccessBeforeCascadingAndDeletesDeviceLast() {
        when(mongo.findById("device-1", Device.class)).thenReturn(device(true));
        service.deleteDevice("device-1");
        var order = inOrder(broker, mongo);
        order.verify(broker).disableDeviceIdentity(IDENTITY);
        order.verify(broker).revokeDeviceIdentity(IDENTITY);
        order.verify(broker).deleteRole(IDENTITY);
        for (String collection : List.of("sensor_readings", "alerts", "alertRules", "maintenance_tasks",
                "generated_reports", "provisioning_sessions", "device_enrollment_operations")) {
            order.verify(mongo).remove(query("deviceId", "device-1"), eq(collection));
        }
        order.verify(mongo).remove(query("_id", "device-1"), eq("devices"));
    }

    @Test
    void neverProvisionedDeviceCanBeDeletedWithoutBrokerManagement() {
        when(mongo.findById("device-1", Device.class)).thenReturn(device(false));
        service.deleteDevice("device-1");
        verifyNoInteractions(broker);
        verify(mongo).remove(query("_id", "device-1"), eq("devices"));
    }

    @Test
    void brokerFailureRetainsAllAccountRecordsForRetry() {
        prepareAccount();
        when(broker.disableDeviceIdentity(IDENTITY)).thenThrow(
                new BrokerManagementException(BrokerManagementFailure.TIMEOUT, "internal connection details"));
        DeletionBlockedException error = assertThrows(DeletionBlockedException.class,
                () -> service.deleteSelf("owner@example.com", "correct"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertFalse(error.getMessage().contains("internal connection"));
        verify(mongo, never()).remove(any(Query.class), anyString());
        verify(users, never()).delete(any());
        verifyNoInteractions(images);
    }

    @Test
    void missingBrokerResourcesAreSuccessfulForIdempotentRetry() {
        when(mongo.findById("device-1", Device.class)).thenReturn(device(true));
        when(broker.inspectEnrollmentStatus(IDENTITY)).thenThrow(missing());
        when(broker.inspectRole(IDENTITY)).thenThrow(missing());
        assertDoesNotThrow(() -> service.deleteDevice("device-1"));
        verify(mongo).remove(query("_id", "device-1"), eq("devices"));
        verify(broker, never()).revokeDeviceIdentity(anyString());
    }

    @Test
    void retryAfterDatabaseFailureFinishesWithAlreadyRevokedIdentity() {
        when(mongo.findById("device-1", Device.class)).thenReturn(device(true));
        doThrow(new IllegalStateException("database unavailable")).doReturn(null)
                .when(mongo).remove(any(Query.class), eq("sensor_readings"));
        assertThrows(IllegalStateException.class, () -> service.deleteDevice("device-1"));
        verify(mongo, never()).remove(any(Query.class), eq("devices"));
        when(broker.inspectEnrollmentStatus(IDENTITY)).thenThrow(missing());
        when(broker.inspectRole(IDENTITY)).thenThrow(missing());
        assertDoesNotThrow(() -> service.deleteDevice("device-1"));
        verify(mongo).remove(query("_id", "device-1"), eq("devices"));
    }

    @Test
    void refusesUnacknowledgedRevocationAndPreservesLocalRecords() {
        when(mongo.findById("device-1", Device.class)).thenReturn(device(true));
        when(broker.revokeDeviceIdentity(IDENTITY)).thenReturn(new BrokerOperationResult("revokeDeviceIdentity", IDENTITY, false));
        assertThrows(DeletionBlockedException.class, () -> service.deleteDevice("device-1"));
        verify(mongo, never()).remove(any(Query.class), anyString());
    }

    @Test
    void neverRevokesAClientWhoseBrokerIdentityDoesNotMatch() {
        when(mongo.findById("device-1", Device.class)).thenReturn(device(true));
        when(broker.inspectEnrollmentStatus(IDENTITY)).thenReturn(
                new BrokerDeviceIdentityStatus(IDENTITY, "unrelated-client", false, true));
        assertThrows(DeletionBlockedException.class, () -> service.deleteDevice("device-1"));
        verify(broker, never()).disableDeviceIdentity(anyString());
        verify(mongo, never()).remove(any(Query.class), anyString());
    }

    @Test
    void keepsRolesThatStillHaveOtherReferences() {
        when(mongo.findById("device-1", Device.class)).thenReturn(device(true));
        when(broker.inspectRole(IDENTITY)).thenReturn(new BrokerRoleStatus(IDENTITY, List.of("other-client"), List.of(), true, true));
        service.deleteDevice("device-1");
        verify(broker, never()).deleteRole(anyString());
        verify(mongo).remove(query("_id", "device-1"), eq("devices"));
    }

    @Test
    void revokesOrphanEnrollmentIdentityBeforeDeletingAccountEvidence() {
        User user = user("owner@example.com", "USER");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("correct", "hash")).thenReturn(true);
        when(mongo.find(any(Query.class), eq(Device.class))).thenReturn(List.of());
        DeviceEnrollmentOperation operation = operation(EnrollmentState.COMPLETED);
        operation.setDeviceId("previously-deleted-device");
        when(mongo.find(any(Query.class), eq(DeviceEnrollmentOperation.class))).thenReturn(List.of(operation));
        service.deleteSelf(user.getEmail(), "correct");
        var order = inOrder(broker, mongo, users);
        order.verify(broker).revokeDeviceIdentity(IDENTITY);
        order.verify(mongo).remove(query("deviceId", new Document("$in", List.of("previously-deleted-device"))), eq("sensor_readings"));
        order.verify(mongo).remove(any(Query.class), eq("device_enrollment_operations"));
        order.verify(users).delete(user);
    }

    @Test
    void brokerMutationInProgressBlocksDeletion() {
        when(mongo.findById("device-1", Device.class)).thenReturn(device(true));
        when(mongo.find(any(Query.class), eq(DeviceEnrollmentOperation.class)))
                .thenReturn(List.of(operation(EnrollmentState.BROKER_CLIENT_PENDING)));
        DeletionBlockedException error = assertThrows(DeletionBlockedException.class,
                () -> service.deleteDevice("device-1"));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verifyNoInteractions(broker);
        verify(mongo, never()).remove(any(Query.class), anyString());
    }

    @Test
    void selfDeleteRejectsWrongPasswordWithoutChangingData() {
        User user = user("owner@example.com", "USER");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThrows(InvalidCredentialsException.class, () -> service.deleteSelf(user.getEmail(), "wrong"));
        verify(users, never()).delete(any());
        verify(mongo, never()).remove(any(Query.class), anyString());
        verifyNoInteractions(broker);
    }

    @Test
    void administratorCannotDeleteTheirOwnAccountThroughAdminPath() {
        User admin = user("admin@example.com", "ADMIN");
        when(users.findById("admin-1")).thenReturn(Optional.of(admin));
        assertThrows(IllegalArgumentException.class, () -> service.deleteUser("admin-1", "ADMIN@example.com"));
        verify(users, never()).delete(any());
        verifyNoInteractions(broker);
    }

    private User prepareAccount() {
        User user = user("owner@example.com", "USER");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("correct", user.getPasswordHash())).thenReturn(true);
        when(mongo.find(any(Query.class), eq(Device.class))).thenReturn(List.of(device(true)));
        return user;
    }

    private Device device(boolean provisioned) {
        Device device = new Device();
        device.setId("device-1"); device.setDeviceKey("NEXA-TEST"); device.setOwnerEmail("owner@example.com");
        if (provisioned) {
            DeviceMqttCredentialInfo credential = new DeviceMqttCredentialInfo();
            credential.setUsername(IDENTITY); device.setMqttCredentialInfo(credential);
        }
        return device;
    }

    private DeviceEnrollmentOperation operation(EnrollmentState state) {
        DeviceEnrollmentOperation operation = new DeviceEnrollmentOperation();
        operation.setDeviceId("device-1"); operation.setOwnerEmail("owner@example.com"); operation.setState(state);
        operation.setBrokerUsername(IDENTITY); operation.setBrokerClientId(IDENTITY); operation.setBrokerRoleName(IDENTITY);
        return operation;
    }

    private BrokerOperationResult result(String operation) {
        return new BrokerOperationResult(operation, IDENTITY, true);
    }

    private BrokerManagementException missing() {
        return new BrokerManagementException(BrokerManagementFailure.RESOURCE_NOT_FOUND, "not found");
    }

    private Query query(String key, Object value) {
        return argThat(q -> q.getQueryObject().equals(new Document(key, value)));
    }

    private User user(String email, String role) {
        User user = new User("Test User", email, "hash", role); user.setId("user-1"); return user;
    }
}
