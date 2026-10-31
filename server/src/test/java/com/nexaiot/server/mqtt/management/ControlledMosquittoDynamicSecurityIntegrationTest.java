package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Explicitly invoked live management harness. It is excluded unless the
 * integration flag is enabled, and it fails closed unless mutation authorization
 * is separately enabled. It does not run during ordinary Maven test execution.
 */
@EnabledIfSystemProperty(named = "nexaiot.integration.enabled", matches = "true")
class ControlledMosquittoDynamicSecurityIntegrationTest {

    private static final String BROKER_URL = "ssl://localhost:1885";
    private static final String MANAGEMENT_USERNAME = "nexaiot-provisioning-management";
    private static final String MANAGEMENT_CLIENT_ID = "nexaiot-provisioning-management";
    private static final String DEVICE_KEY = "NEXA-8CD8C0F1559E";

    @Test
    void runProvisioningAndOwnedCleanup() throws Throwable {
        requireMutationAuthorization();
        String caCertificate = requiredCaCertificate();
        String managementPassword = managementPassword();

        MosquittoDynamicSecurityClient broker = new MosquittoDynamicSecurityClient(
                new ObjectMapper(), true, BROKER_URL, MANAGEMENT_CLIENT_ID,
                MANAGEMENT_USERNAME, managementPassword, true, 5, caCertificate);
        DeviceCredentialGenerator generator = new DeviceCredentialGenerator();
        GeneratedDeviceCredential device = generator.generate(DEVICE_KEY);
        String roleName = "nexaiot-device-" + DEVICE_KEY;
        IntegrationRunReceipt receipt = IntegrationRunReceipt.create(
                DEVICE_KEY, device.username(), device.clientId(), roleName);
        Path receiptDirectory = IntegrationRunReceiptStore.defaultDirectory();
        IntegrationRunReceiptStore.write(receiptDirectory, receipt);

        boolean roleCreated = false;
        boolean clientCreated = false;
        Throwable primaryFailure = null;
        try {
            expectAbsentClient(broker, device.username());
            expectAbsentRole(broker, roleName);

            // Build the complete role before creating the client identity.
            broker.createDeviceRolePermissions(DEVICE_KEY);
            roleCreated = true;
            receipt = newReceipt(receipt, false, true, "ROLE_CREATED");
            IntegrationRunReceiptStore.write(receiptDirectory, receipt);

            broker.createDeviceIdentity(device.username(), device.clientId(), device.password());
            clientCreated = true;
            receipt = newReceipt(receipt, true, true, "CLIENT_CREATED");
            IntegrationRunReceiptStore.write(receiptDirectory, receipt);

            broker.assignDeviceRole(device.username(), DEVICE_KEY);
            receipt = newReceipt(receipt, true, true, "ROLE_ASSIGNED");
            IntegrationRunReceiptStore.write(receiptDirectory, receipt);

            BrokerDeviceProvisioningStatus provisioning =
                    broker.verifyDeviceProvisioning(device.username(), DEVICE_KEY);
            assertEquals(true, provisioning.verified());
        } catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                cleanupOwnedResources(broker, receipt, clientCreated, roleCreated, receiptDirectory);
            } catch (Throwable cleanupFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private void cleanupOwnedResources(
            MosquittoDynamicSecurityClient broker,
            IntegrationRunReceipt receipt,
            boolean clientCreated,
            boolean roleCreated,
            Path receiptDirectory
    ) throws Throwable {
        try {
            boolean clientPresent = clientPresent(broker, receipt.username());
            boolean rolePresent = rolePresent(broker, receipt.roleName());

            if (clientPresent && !clientCreated) {
                throw IntegrationResourceOwnership.manualReconciliation(
                        "Client exists after an unknown creation outcome.");
            }
            if (rolePresent && !roleCreated) {
                throw IntegrationResourceOwnership.manualReconciliation(
                        "Role exists after an unknown creation outcome.");
            }

            if (clientPresent) {
                // Ownership must be proven before disable, revoke, or delete.
                BrokerDeviceIdentityStatus beforeMutation =
                        broker.inspectEnrollmentStatus(receipt.username());
                IntegrationResourceOwnership.requireClientOwnership(receipt, beforeMutation);
                broker.disableDeviceIdentity(receipt.username());
                BrokerDeviceIdentityStatus disabled = broker.inspectEnrollmentStatus(receipt.username());
                IntegrationResourceOwnership.requireClientOwnership(receipt, disabled);
                assertEquals(true, disabled.disabled());

                broker.revokeDeviceIdentity(receipt.username());
                expectAbsentClient(broker, receipt.username());
            }
            if (rolePresent) {
                BrokerRoleStatus role = broker.inspectRole(receipt.roleName());
                IntegrationResourceOwnership.requireRoleOwnership(receipt, role);
                broker.deleteRole(receipt.roleName());
                expectAbsentRole(broker, receipt.roleName());
            }
            IntegrationRunReceiptStore.write(receiptDirectory,
                    newReceipt(receipt, false, false, "CLEANED"));
        } catch (Throwable exception) {
            try {
                IntegrationRunReceiptStore.write(receiptDirectory,
                        newReceipt(receipt, clientCreated, roleCreated,
                                "MANUAL_RECONCILIATION_REQUIRED"));
            } catch (Throwable receiptFailure) {
                exception.addSuppressed(receiptFailure);
            }
            throw exception;
        }
    }

    private boolean clientPresent(MosquittoDynamicSecurityClient broker, String username) {
        try {
            broker.inspectEnrollmentStatus(username);
            return true;
        } catch (BrokerManagementException exception) {
            if (exception.getFailure() == BrokerManagementFailure.RESOURCE_NOT_FOUND) {
                return false;
            }
            throw exception;
        }
    }

    private boolean rolePresent(MosquittoDynamicSecurityClient broker, String roleName) {
        try {
            broker.inspectRole(roleName);
            return true;
        } catch (BrokerManagementException exception) {
            if (exception.getFailure() == BrokerManagementFailure.RESOURCE_NOT_FOUND) {
                return false;
            }
            throw exception;
        }
    }

    private IntegrationRunReceipt newReceipt(
            IntegrationRunReceipt old,
            boolean clientCreated,
            boolean roleCreated,
            String status
    ) {
        return new IntegrationRunReceipt(old.runId(), old.deviceKey(), old.username(), old.clientId(),
                old.roleName(), clientCreated, roleCreated, status, old.createdAt());
    }

    private void expectAbsentClient(MosquittoDynamicSecurityClient broker, String username) {
        try {
            broker.inspectEnrollmentStatus(username);
            fail("Pre-existing or uncleared MQTT client resource: " + username);
        } catch (BrokerManagementException exception) {
            if (exception.getFailure() != BrokerManagementFailure.RESOURCE_NOT_FOUND) {
                throw exception;
            }
        }
    }

    private void expectAbsentRole(MosquittoDynamicSecurityClient broker, String roleName) {
        try {
            broker.inspectRole(roleName);
            fail("Pre-existing or uncleared MQTT role resource: " + roleName);
        } catch (BrokerManagementException exception) {
            if (exception.getFailure() != BrokerManagementFailure.RESOURCE_NOT_FOUND) {
                throw exception;
            }
        }
    }

    private void requireMutationAuthorization() {
        if (!"true".equalsIgnoreCase(System.getProperty("nexaiot.integration.enabled"))
                || !"true".equalsIgnoreCase(System.getProperty("nexaiot.integration.mutations"))) {
            throw new IllegalStateException(
                    "nexaiot.integration.enabled=true and nexaiot.integration.mutations=true "
                            + "are required for broker mutations.");
        }
    }

    private String requiredCaCertificate() {
        String localAppData = System.getenv("LOCALAPPDATA");
        String path = System.getenv("NEXAIOT_MQTT_CA_CERTIFICATE");
        if (path == null || path.isBlank()) {
            if (localAppData == null || localAppData.isBlank()) {
                throw new IllegalStateException("LOCALAPPDATA is required for the TLS CA path.");
            }
            path = Path.of(localAppData, "NexaIoT", "mosquitto-tls-staging",
                    "certs", "ca", "ca.crt").toString();
        }
        if (!Files.isRegularFile(Path.of(path))) {
            throw new IllegalStateException("Configured TLS CA certificate is unavailable.");
        }
        return path;
    }

    private String managementPassword() {
        String password = System.getenv("NEXAIOT_MQTT_MANAGEMENT_PASSWORD");
        if (password != null && !password.isBlank()) {
            return password;
        }
        if (System.console() == null) {
            throw new IllegalStateException(
                    "Use an interactive console or NEXAIOT_MQTT_MANAGEMENT_PASSWORD.");
        }
        char[] entered = System.console().readPassword("Management password: ");
        if (entered == null || entered.length == 0) {
            throw new IllegalStateException("Management password is required.");
        }
        return new String(entered);
    }
}
