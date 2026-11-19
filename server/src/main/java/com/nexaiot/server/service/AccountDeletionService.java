package com.nexaiot.server.service;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceNotFoundException;
import com.nexaiot.server.device.DeviceProvisioningStatus;
import com.nexaiot.server.enrollment.DeviceEnrollmentOperation;
import com.nexaiot.server.enrollment.EnrollmentState;
import com.nexaiot.server.exception.DeletionBlockedException;
import com.nexaiot.server.exception.InvalidCredentialsException;
import com.nexaiot.server.exception.UserNotFoundException;
import com.nexaiot.server.model.User;
import com.nexaiot.server.mqtt.MqttTopicSecurity;
import com.nexaiot.server.mqtt.management.BrokerManagementClient;
import com.nexaiot.server.mqtt.management.BrokerManagementException;
import com.nexaiot.server.mqtt.management.BrokerManagementFailure;
import com.nexaiot.server.mqtt.management.BrokerOperationResult;
import com.nexaiot.server.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared user/admin cleanup. Revoke broker access before removing recovery data. */
@Service
public class AccountDeletionService {
    private static final List<String> OWNED_COLLECTIONS = List.of(
            "alerts", "alertRules", "maintenance_tasks", "generated_reports",
            "provisioning_sessions", "device_enrollment_operations");
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MongoTemplate mongo;
    private final GridFsTemplate gridFs;
    private final BrokerManagementClient broker;

    public AccountDeletionService(UserRepository users, PasswordEncoder encoder,
            MongoTemplate mongo, ObjectProvider<GridFsTemplate> gridFs,
            BrokerManagementClient broker) {
        this.users = users;
        this.encoder = encoder;
        this.mongo = mongo;
        this.gridFs = gridFs.getIfAvailable();
        this.broker = broker;
    }

    public void deleteSelf(String email, String password) {
        User user = findUser(email);
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        deleteUser(user);
    }

    public void deleteUser(String id, String requester) {
        User user = users.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));
        if (user.getEmail().equalsIgnoreCase(requester)) {
            throw new IllegalArgumentException("An administrator cannot delete the current account.");
        }
        deleteUser(user);
    }

    public void deleteDevice(String id) {
        Device device = mongo.findById(id, Device.class);
        if (device == null) throw new DeviceNotFoundException("Device not found");
        List<DeviceEnrollmentOperation> operations = mongo.find(
                Query.query(Criteria.where("deviceId").is(id)), DeviceEnrollmentOperation.class);
        Set<String> identities = new LinkedHashSet<>();
        collectDeviceIdentity(device, operations, identities);
        revokeAll(identities);
        mongo.remove(Query.query(Criteria.where("deviceId").is(id)), "sensor_readings");
        for (String collection : OWNED_COLLECTIONS) {
            mongo.remove(Query.query(Criteria.where("deviceId").is(id)), collection);
        }
        // Keep the device until all related cleanup succeeds, so failures can be retried.
        mongo.remove(Query.query(Criteria.where("_id").is(id)), "devices");
    }

    private void deleteUser(User user) {
        String email = normalize(user.getEmail());
        List<Device> devices = mongo.find(Query.query(Criteria.where("ownerEmail").is(email)), Device.class);
        List<DeviceEnrollmentOperation> operations = mongo.find(
                Query.query(Criteria.where("ownerEmail").is(email)), DeviceEnrollmentOperation.class);
        Set<String> identities = new LinkedHashSet<>();
        for (Device device : devices) {
            collectDeviceIdentity(device, operations.stream()
                    .filter(operation -> device.getId().equals(operation.getDeviceId())).toList(), identities);
        }
        // Include enrollment evidence for devices already removed by the old deletion path.
        for (DeviceEnrollmentOperation operation : operations) {
            if (devices.stream().noneMatch(device -> device.getId().equals(operation.getDeviceId()))) {
                collectOrphanIdentity(operation, identities);
            }
        }
        // A failure here leaves all database records in place for a retry.
        revokeAll(identities);
        List<String> deviceIds = java.util.stream.Stream.concat(
                devices.stream().map(Device::getId),
                operations.stream().map(DeviceEnrollmentOperation::getDeviceId))
                .filter(this::hasText).distinct().toList();
        if (!deviceIds.isEmpty()) {
            mongo.remove(Query.query(Criteria.where("deviceId").in(deviceIds)), "sensor_readings");
        }
        for (String collection : OWNED_COLLECTIONS) {
            Criteria owned = Criteria.where("ownerEmail").is(email);
            Query query = deviceIds.isEmpty() ? Query.query(owned)
                    : Query.query(new Criteria().orOperator(owned, Criteria.where("deviceId").in(deviceIds)));
            mongo.remove(query, collection);
        }
        mongo.remove(Query.query(new Criteria().orOperator(
                Criteria.where("userId").is(user.getId()), Criteria.where("userEmail").is(email))),
                "password_reset_tokens");
        if (gridFs != null) {
            Criteria imageOwner = new Criteria().orOperator(
                    Criteria.where("metadata.ownerEmail").is(email),
                    Criteria.where("metadata.ownerUserId").is(user.getId()));
            mongoImageCleanup(user, imageOwner);
        }
        // Delete account and device records last; retries remain possible after partial cleanup.
        mongo.remove(Query.query(Criteria.where("ownerEmail").is(email)), "devices");
        users.delete(user);
    }

    private void mongoImageCleanup(User user, Criteria imageOwner) {
        if (hasText(user.getProfileImageId())) {
            org.bson.types.ObjectId imageId;
            try {
                imageId = new org.bson.types.ObjectId(user.getProfileImageId());
            } catch (IllegalArgumentException exception) {
                throw blocked("Profile image ownership needs administrator review before deletion.");
            }
            // Older profile images may lack owner metadata; only use the account's exact reference.
            Criteria legacyImage = new Criteria().andOperator(
                    Criteria.where("_id").is(imageId),
                    Criteria.where("metadata.ownerEmail").is(null),
                    Criteria.where("metadata.ownerUserId").is(null));
            gridFs.delete(Query.query(new Criteria().orOperator(imageOwner, legacyImage)));
        } else {
            gridFs.delete(Query.query(imageOwner));
        }
    }

    private void collectDeviceIdentity(Device device, List<DeviceEnrollmentOperation> operations,
            Set<String> identities) {
        boolean credentialRecorded = device.getMqttCredentialInfo() != null
                && hasText(device.getMqttCredentialInfo().getUsername());
        boolean provisioningStarted = device.getProvisioningInfo() != null
                && device.getProvisioningInfo().getStatus() != null
                && device.getProvisioningInfo().getStatus() != DeviceProvisioningStatus.REGISTERED;
        if (!credentialRecorded && !provisioningStarted && operations.isEmpty()) return;
        String expected = identityFor(device.getDeviceKey());
        if (credentialRecorded && !expected.equals(device.getMqttCredentialInfo().getUsername())) {
            throw blocked("Device identity needs administrator review before deletion.");
        }
        for (DeviceEnrollmentOperation operation : operations) {
            requireQuiescent(operation);
            requireMatchingIdentity(operation, expected);
        }
        identities.add(expected);
    }

    private void collectOrphanIdentity(DeviceEnrollmentOperation operation, Set<String> identities) {
        requireQuiescent(operation);
        String username = operation.getBrokerUsername();
        if (!hasText(username)) {
            if (operation.getState() == EnrollmentState.REQUESTED
                    || operation.getState() == EnrollmentState.CANCELLED) return;
            throw blocked("Enrollment identity needs administrator review before deletion.");
        }
        String prefix = "nexaiot-device-";
        if (!username.startsWith(prefix)) {
            throw blocked("Enrollment identity needs administrator review before deletion.");
        }
        String expected = identityFor(username.substring(prefix.length()));
        requireMatchingIdentity(operation, expected);
        identities.add(expected);
    }

    private void requireQuiescent(DeviceEnrollmentOperation operation) {
        if (operation.getState() == EnrollmentState.BROKER_CLIENT_PENDING
                || operation.getState() == EnrollmentState.ACL_PENDING) {
            throw blocked("Provisioning is in progress. Wait for it to finish or reconcile it before deleting.");
        }
    }

    private void requireMatchingIdentity(DeviceEnrollmentOperation operation, String expected) {
        if ((hasText(operation.getBrokerUsername()) && !expected.equals(operation.getBrokerUsername()))
                || (hasText(operation.getBrokerClientId()) && !expected.equals(operation.getBrokerClientId()))
                || (hasText(operation.getBrokerRoleName()) && !expected.equals(operation.getBrokerRoleName()))) {
            throw blocked("Enrollment identity needs administrator review before deletion.");
        }
    }

    private String identityFor(String deviceKey) {
        try {
            MqttTopicSecurity.validateDeviceKey(deviceKey);
        } catch (IllegalArgumentException exception) {
            throw blocked("Device identity needs administrator review before deletion.");
        }
        return "nexaiot-device-" + deviceKey;
    }

    private void revokeAll(Set<String> identities) {
        try {
            for (String identity : identities) revokeIdentity(identity);
        } catch (BrokerManagementException exception) {
            throw new DeletionBlockedException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Device access could not be revoked. No records were deleted; please retry later.");
        }
    }

    private void revokeIdentity(String identity) {
        try {
            var status = broker.inspectEnrollmentStatus(identity);
            if (status == null || !status.acknowledged()
                    || !identity.equals(status.username()) || !identity.equals(status.clientId())) {
                throw blocked("Broker identity needs administrator review before deletion.");
            }
            if (!status.disabled()) requireAcknowledged(broker.disableDeviceIdentity(identity), identity);
            requireAcknowledged(broker.revokeDeviceIdentity(identity), identity);
        } catch (BrokerManagementException exception) {
            // Already absent is a successful revocation, including a retry after partial cleanup.
            if (exception.getFailure() != BrokerManagementFailure.RESOURCE_NOT_FOUND) throw exception;
        }
        try {
            var role = broker.inspectRole(identity);
            if (role == null || !role.acknowledged() || !role.referencesKnown()
                    || !identity.equals(role.roleName())) {
                throw blocked("Broker role ownership needs administrator review before deletion.");
            }
            // Never remove a role still used by another client or group.
            if (role.referencedClients().isEmpty() && role.referencedGroups().isEmpty()) {
                requireAcknowledged(broker.deleteRole(identity), identity);
            }
        } catch (BrokerManagementException exception) {
            if (exception.getFailure() != BrokerManagementFailure.RESOURCE_NOT_FOUND) throw exception;
        }
    }

    private void requireAcknowledged(BrokerOperationResult result, String identity) {
        if (result == null || !result.acknowledged() || !identity.equals(result.target())) {
            throw new BrokerManagementException(BrokerManagementFailure.INVALID_RESPONSE,
                    "Deletion acknowledgement is incomplete.");
        }
    }

    private DeletionBlockedException blocked(String message) {
        return new DeletionBlockedException(HttpStatus.CONFLICT, message);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private User findUser(String email) {
        return users.findByEmail(normalize(email)).orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
