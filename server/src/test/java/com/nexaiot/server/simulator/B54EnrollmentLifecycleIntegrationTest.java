package com.nexaiot.server.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityControl;
import com.nexaiot.server.device.CapabilityControlType;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.enrollment.DeviceEnrollmentOperation;
import com.nexaiot.server.enrollment.DeviceProvisioningOrchestrator;
import com.nexaiot.server.enrollment.EnrollmentConcurrencyException;
import com.nexaiot.server.enrollment.EnrollmentOperationService;
import com.nexaiot.server.enrollment.EnrollmentReconciliationPlanner;
import com.nexaiot.server.enrollment.EnrollmentState;
import com.nexaiot.server.enrollment.BrokerEnrollmentSnapshot;
import com.nexaiot.server.enrollment.ReconciliationOutcome;
import com.nexaiot.server.mqtt.management.BrokerDeviceIdentity;
import com.nexaiot.server.mqtt.management.BrokerDeviceProvisioningStatus;
import com.nexaiot.server.mqtt.management.BrokerManagementClient;
import com.nexaiot.server.mqtt.management.BrokerManagementException;
import com.nexaiot.server.mqtt.management.BrokerManagementFailure;
import com.nexaiot.server.mqtt.management.BrokerOperationResult;
import com.nexaiot.server.mqtt.management.BrokerRoleStatus;
import com.nexaiot.server.mqtt.management.DeviceCredentialGenerator;
import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/**
 * Offline B5.4 contract test. All persistence, broker management, and MQTT
 * transport calls are local doubles; no Spring startup hook or live endpoint
 * is involved.
 */
class B54EnrollmentLifecycleIntegrationTest {

    private static final String OWNER = "owner@example.com";
    private static final String DEVICE_KEY = "NEXA-B54-01";
    private static final String OPERATION_ID = "operation-b54-1";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void completeLifecycleReachesActiveAndPublishesTelemetryStateAndAck() throws Exception {
        Rig rig = new Rig();
        try (SimulatorMqttClient client = rig.connectedClient()) {
            assertEquals(SimulatorConnectionState.ACTIVATION_PENDING, client.state());
            client.markActivationVerified(rig.validEvidence());
            assertEquals(SimulatorConnectionState.ACTIVE, client.state());
            client.publishTelemetry(new SimulatorTelemetry(DEVICE_KEY, NOW,
                    Map.of("temperature", 22.5)), rig.codec, 1);
            client.publishState(new SimulatorState(Map.of("power", true)), rig.codec, 1);
            SimulatorCommandResult result = rig.processor.process(
                    SimulatorMqttTopics.commands(rig.identity), rig.command("cmd-1", true));
            client.publishCommandResult(rig.codec.commandResult(result), 1);
            assertEquals(SimulatorCommandResult.Status.ACCEPTED, result.status());
            assertEquals(true, rig.processor.currentState().values().get("power"));
            assertEquals(SimulatorMqttTopics.commandResults(rig.identity), rig.connection.lastTopic);
        }
        assertEquals(SimulatorLifecycleState.ACTIVE, rig.lifecycle.state());
    }

    @Test void invalidPairingTokenIsRejected() {
        com.nexaiot.server.provisioning.PairingTokenService tokens =
                new com.nexaiot.server.provisioning.PairingTokenService();
        String token = tokens.generateToken();
        assertFalse(tokens.matches("wrong-token", tokens.hashToken(token)));
    }

    @Test void incorrectHardwareIdentityIsRejected() {
        Rig rig = new Rig();
        assertThrows(IllegalArgumentException.class, () -> rig.bootstrap.deliver(rig.grant(),
                SimulatorDeviceIdentity.forDeviceKey(DEVICE_KEY, "wrong-hardware"),
                new GeneratedDeviceCredential(rig.identity.clientId(), rig.identity.clientId(), "pw")));
    }

    @Test void bootstrapGrantRemainsBoundToTheAuthorizedOwner() {
        Rig rig = new Rig();
        SimulatorBootstrapGrant grant = rig.bootstrap.authorize(rig.identity, OWNER, OPERATION_ID);
        assertEquals(OWNER, grant.ownerEmail());
        assertFalse("other@example.com".equals(grant.ownerEmail()));
    }

    @Test void duplicateClaimIsNotAcceptedByLifecycle() {
        SimulatorLifecycle lifecycle = new SimulatorLifecycle();
        lifecycle.pairingStarted();
        lifecycle.pairingClaimed();
        assertThrows(IllegalStateException.class, lifecycle::pairingClaimed);
    }

    @Test void duplicateEnrollmentDoesNotRepeatACompletedSimulatorTransition() {
        SimulatorLifecycle lifecycle = readyLifecycle();
        assertThrows(IllegalStateException.class, lifecycle::enrollmentAuthorized);
    }

    @Test void orchestratorUsesTheProductionStateBoundaryAndStopsAtCredentialDelivery() {
        DeviceRepository devices = mock(DeviceRepository.class);
        EnrollmentOperationService operations = mock(EnrollmentOperationService.class);
        BrokerManagementClient broker = mock(BrokerManagementClient.class);
        Device device = new Device();
        device.setId("device-id");
        device.setDeviceKey(DEVICE_KEY);
        DeviceEnrollmentOperation requested = operation(EnrollmentState.REQUESTED);
        DeviceEnrollmentOperation pending = operation(EnrollmentState.BROKER_CLIENT_PENDING);
        DeviceEnrollmentOperation created = operation(EnrollmentState.BROKER_CLIENT_CREATED);
        DeviceEnrollmentOperation aclPending = operation(EnrollmentState.ACL_PENDING);
        DeviceEnrollmentOperation aclAssigned = operation(EnrollmentState.ACL_ASSIGNED);
        DeviceEnrollmentOperation verified = operation(EnrollmentState.BROKER_VERIFIED);
        DeviceEnrollmentOperation delivery = operation(EnrollmentState.CREDENTIAL_DELIVERY_PENDING);
        when(devices.findByIdAndOwnerEmail("device-id", OWNER)).thenReturn(Optional.of(device));
        when(operations.transition(OPERATION_ID, EnrollmentState.REQUESTED,
                EnrollmentState.BROKER_CLIENT_PENDING, OWNER)).thenReturn(pending);
        when(operations.recordBrokerIdentity(eq(OPERATION_ID), eq(OWNER), anyString(), anyString(),
                eq("nexaiot-device-" + DEVICE_KEY), anyString())).thenReturn(created);
        when(operations.transition(OPERATION_ID, EnrollmentState.BROKER_CLIENT_CREATED,
                EnrollmentState.ACL_PENDING, OWNER)).thenReturn(aclPending);
        when(operations.transition(OPERATION_ID, EnrollmentState.ACL_PENDING,
                EnrollmentState.ACL_ASSIGNED, OWNER)).thenReturn(aclAssigned);
        when(operations.markBrokerVerified(OPERATION_ID, OWNER)).thenReturn(verified);
        when(operations.transition(OPERATION_ID, EnrollmentState.BROKER_VERIFIED,
                EnrollmentState.CREDENTIAL_DELIVERY_PENDING, OWNER)).thenReturn(delivery);
        when(broker.createDeviceIdentity(anyString(), anyString(), anyString()))
                .thenReturn(new BrokerDeviceIdentity("client", "client"));
        when(broker.assignDevicePermissions(anyString(), eq(DEVICE_KEY)))
                .thenReturn(new BrokerOperationResult("assign", DEVICE_KEY, true));
        when(broker.verifyDeviceProvisioning(anyString(), eq(DEVICE_KEY)))
                .thenReturn(new BrokerDeviceProvisioningStatus(
                        "nexaiot-device-" + DEVICE_KEY, "nexaiot-device-" + DEVICE_KEY,
                        "nexaiot-device-" + DEVICE_KEY, 10, 10, true, true));

        DeviceEnrollmentOperation result = new DeviceProvisioningOrchestrator(
                devices, operations, broker, new DeviceCredentialGenerator())
                .provision(requested, OWNER);

        assertEquals(EnrollmentState.CREDENTIAL_DELIVERY_PENDING, result.getState());
        verify(broker).createDeviceIdentity(anyString(), anyString(), anyString());
        verify(operations, never()).complete(anyString(), anyString());
    }

    @Test void concurrentProvisioningFailsClosedWhenCompareAndSetLoses() {
        EnrollmentOperationService operations = mock(EnrollmentOperationService.class);
        when(operations.transition(OPERATION_ID, EnrollmentState.REQUESTED,
                EnrollmentState.BROKER_CLIENT_PENDING, OWNER))
                .thenThrow(new EnrollmentConcurrencyException("changed concurrently"));
        assertThrows(EnrollmentConcurrencyException.class, () -> operations.transition(
                OPERATION_ID, EnrollmentState.REQUESTED, EnrollmentState.BROKER_CLIENT_PENDING, OWNER));
    }

    @Test void brokerFailureDoesNotAdvanceToCredentials() {
        assertEquals(ReconciliationOutcome.RECONCILIATION_REQUIRED,
                new EnrollmentReconciliationPlanner().classify(
                        operation(EnrollmentState.RECONCILIATION_REQUIRED),
                        new BrokerEnrollmentSnapshot(false, false, false, false,
                                false, false, true, false)).outcome());
    }

    @Test void partialAclFailsClosed() {
        assertEquals(ReconciliationOutcome.RECONCILIATION_REQUIRED,
                new EnrollmentReconciliationPlanner().classify(
                        operation(EnrollmentState.BROKER_CLIENT_CREATED),
                        new BrokerEnrollmentSnapshot(true, true, true, false,
                                false, true, true, false)).outcome());
    }

    @Test void ambiguousBrokerInspectionRequiresManualReview() {
        assertEquals(ReconciliationOutcome.MANUAL_REVIEW_REQUIRED,
                new EnrollmentReconciliationPlanner().classify(
                        operation(EnrollmentState.RECONCILIATION_REQUIRED),
                        new BrokerEnrollmentSnapshot(true, true, true, true,
                                false, true, false, false)).outcome());
    }

    @Test void lostCredentialMaterialRequiresReissuance() {
        DeviceEnrollmentOperation operation = operation(EnrollmentState.CREDENTIAL_DELIVERY_PENDING);
        operation.setBrokerUsername(rigIdentity());
        operation.setBrokerClientId(rigIdentity());
        operation.setBrokerRoleName(rigIdentity());
        assertEquals(ReconciliationOutcome.CREDENTIAL_REISSUANCE_REQUIRED,
                new EnrollmentReconciliationPlanner().classify(operation,
                        new BrokerEnrollmentSnapshot(true, true, true, true,
                                false, true, true, false)).outcome());
    }

    @Test void bootstrapReplayIsRejected() {
        Rig rig = new Rig();
        SimulatorBootstrapGrant grant = rig.grant();
        GeneratedDeviceCredential credential = rig.credential();
        rig.bootstrap.deliver(grant, rig.identity, credential).close();
        assertThrows(IllegalArgumentException.class,
                () -> rig.bootstrap.deliver(grant, rig.identity, credential));
    }

    @Test void invalidSimulatorConnectionIsClassified() throws Exception {
        Rig rig = new Rig();
        rig.connection.connectFailure = new SimulatorMqttException(
                SimulatorMqttFailure.INVALID_CREDENTIALS, "rejected");
        try (SimulatorMqttClient client = rig.client()) {
            client.acceptBootstrap(rig.grant(), rig.identity, rig.credential());
            assertThrows(SimulatorMqttException.class, client::connect);
            assertEquals(SimulatorMqttFailure.INVALID_CREDENTIALS, client.lastFailure());
        }
    }

    @Test void missingActivationApprovalBlocksPublication() throws Exception {
        Rig rig = new Rig();
        try (SimulatorMqttClient client = rig.connectedClient()) {
            assertThrows(SimulatorMqttException.class,
                    () -> client.publishTelemetry(new SimulatorTelemetry(DEVICE_KEY, NOW,
                            Map.of("temperature", 20)), rig.codec, 1));
        }
    }

    @Test void staleActivationEvidenceIsRejected() throws Exception {
        Rig rig = new Rig();
        try (SimulatorMqttClient client = rig.connectedClient()) {
            assertThrows(IllegalArgumentException.class, () -> client.markActivationVerified(
                    new SimulatorActivationEvidence(DEVICE_KEY, OPERATION_ID,
                            NOW.minusSeconds(120), "proof")));
        }
    }

    @Test void validTelemetryAfterActivationUsesExactTopic() throws Exception {
        Rig rig = new Rig();
        try (SimulatorMqttClient client = rig.activeClient()) {
            client.publishTelemetry(new SimulatorTelemetry(DEVICE_KEY, NOW,
                    Map.of("temperature", 20)), rig.codec, 1);
            assertEquals(SimulatorMqttTopics.telemetry(rig.identity), rig.connection.lastTopic);
        }
    }

    @Test void telemetryBeforeActivationIsBlocked() throws Exception {
        Rig rig = new Rig();
        try (SimulatorMqttClient client = rig.connectedClient()) {
            assertThrows(SimulatorMqttException.class, () -> client.publishTelemetry(
                    new SimulatorTelemetry(DEVICE_KEY, NOW, Map.of("temperature", 20)),
                    rig.codec, 1));
        }
    }

    @Test void validCommandProducesCorrelatedAcknowledgement() throws Exception {
        Rig rig = new Rig();
        SimulatorCommandResult result = rig.processor.process(
                SimulatorMqttTopics.commands(rig.identity), rig.command("cmd-1", true));
        assertEquals(SimulatorCommandResult.Status.ACCEPTED, result.status());
        assertEquals("cmd-1", result.commandId());
        assertEquals("corr-1", result.correlationId());
        assertNotNull(rig.codec.commandResult(result));
    }

    @Test void unsupportedCommandIsRejected() {
        Rig rig = new Rig();
        SimulatorCommandResult result = rig.processor.process(
                SimulatorMqttTopics.commands(rig.identity), rig.command("cmd-bad", "unsupported"));
        assertEquals(SimulatorCommandResult.Status.REJECTED, result.status());
    }

    @Test void crossDeviceTopicIsRejected() {
        Rig rig = new Rig();
        assertThrows(IllegalArgumentException.class, () -> rig.processor.process(
                "nexaiot/devices/NEXA-OTHER/commands", rig.command("cmd-1", true)));
    }

    @Test void disconnectAndReconnectAreBounded() throws Exception {
        Rig rig = new Rig();
        try (SimulatorMqttClient client = rig.activeClient()) {
            rig.connection.disconnectListener.accept(SimulatorMqttFailure.UNEXPECTED_DISCONNECT);
            assertEquals(SimulatorConnectionState.DISCONNECTED, client.state());
            client.reconnect();
            assertEquals(SimulatorConnectionState.ACTIVATION_PENDING, client.state());
            assertEquals(1, rig.factory.createCount);
            assertEquals(2, rig.connection.connectCount);
            assertEquals(0, client.reconnectAttempts());
        }
    }

    @Test void credentialRevocationBlocksReuse() throws Exception {
        Rig rig = new Rig();
        SimulatorMqttClient client = rig.activeClient();
        client.revokeCredentials();
        assertEquals(SimulatorConnectionState.REVOKED, client.state());
        assertThrows(IllegalStateException.class, client::connect);
        client.close();
    }

    @Test void interruptedActivationDoesNotChangeLifecycleToActive() throws Exception {
        Rig rig = new Rig();
        try (SimulatorMqttClient client = rig.connectedClient()) {
            assertEquals(SimulatorLifecycleState.CONNECTED, rig.lifecycle.state());
            assertEquals(SimulatorConnectionState.ACTIVATION_PENDING, client.state());
        }
    }

    @Test void shutdownIsDeterministicAndClosesTransport() throws Exception {
        Rig rig = new Rig();
        SimulatorMqttClient client = rig.connectedClient();
        client.shutdown();
        assertTrue(rig.connection.closed);
        assertEquals(SimulatorConnectionState.STOPPED, client.state());
    }

    @Test void secretRedactionDoesNotExposeCredential() {
        GeneratedDeviceCredential credential = new GeneratedDeviceCredential(
                "user", "client", "offline-secret");
        assertFalse(credential.toString().contains("offline-secret"));
        assertFalse(new SimulatorCommandResult("id", "corr", "power",
                SimulatorCommandResult.Status.ACCEPTED, null, NOW).toString().contains("offline-secret"));
    }

    @Test void brokerManagementIsNotImplicitlyEnabledByOfflineHarness() {
        assertFalse(Boolean.parseBoolean(System.getProperty("MQTT_MANAGEMENT_ENABLED", "false")));
    }

    @Test void simulatorHasNoAutomaticStartupHook() {
        Rig rig = new Rig();
        SimulatorMqttClient client = rig.client();
        assertEquals(SimulatorConnectionState.BOOTSTRAP_PENDING, client.state());
        assertEquals(0, rig.factory.createCount);
        assertEquals(0, rig.connection.connectCount);
    }

    private static DeviceEnrollmentOperation operation(EnrollmentState state) {
        DeviceEnrollmentOperation operation = new DeviceEnrollmentOperation();
        operation.setId(OPERATION_ID);
        operation.setDeviceId("device-id");
        operation.setOwnerEmail(OWNER);
        operation.setState(state);
        operation.setVersion(0);
        return operation;
    }

    private static SimulatorLifecycle readyLifecycle() {
        SimulatorLifecycle lifecycle = new SimulatorLifecycle();
        lifecycle.pairingStarted();
        lifecycle.pairingClaimed();
        lifecycle.enrollmentAuthorized();
        lifecycle.brokerProvisioned();
        lifecycle.credentialsDelivered();
        lifecycle.connected();
        lifecycle.activationVerified();
        return lifecycle;
    }

    private static String rigIdentity() {
        return "nexaiot-device-" + DEVICE_KEY;
    }

    private static final class Rig {
        private final SimulatorDeviceIdentity identity =
                SimulatorDeviceIdentity.forDeviceKey(DEVICE_KEY, "hardware-hash");
        private final SimulatorMqttConfiguration configuration = new SimulatorMqttConfiguration(
                URI.create("ssl://127.0.0.1:1885"), Path.of("offline-ca.crt"), identity.clientId());
        private final LocalSimulatorBootstrapAdapter bootstrap = new LocalSimulatorBootstrapAdapter(
                true, Duration.ofSeconds(30), Clock.fixed(NOW, ZoneOffset.UTC));
        private final FakeFactory factory = new FakeFactory();
        private final FakeConnection connection = factory.connection;
        private final SimulatorLifecycle lifecycle = readyLifecycleBeforeCredentials();
        private final CapabilityManifest manifest = manifest();
        private final SimulatorMessageCodec codec = new SimulatorMessageCodec(new ObjectMapper());
        private final SimulatorCommandProcessor processor = new SimulatorCommandProcessor(
                identity, manifest, codec, Clock.fixed(NOW, ZoneOffset.UTC));

        private SimulatorLifecycle readyLifecycleBeforeCredentials() {
            SimulatorLifecycle value = new SimulatorLifecycle();
            value.pairingStarted();
            value.pairingClaimed();
            value.enrollmentAuthorized();
            value.brokerProvisioned();
            return value;
        }

        private GeneratedDeviceCredential credential() {
            return new GeneratedDeviceCredential(identity.clientId(), identity.clientId(), "offline-password");
        }

        private SimulatorBootstrapGrant grant() {
            return bootstrap.authorize(identity, OWNER, OPERATION_ID);
        }

        private SimulatorActivationEvidence validEvidence() {
            return new SimulatorActivationEvidence(DEVICE_KEY, OPERATION_ID, NOW, "offline-proof");
        }

        private SimulatorMqttClient client() {
            return new SimulatorMqttClient(identity, configuration, bootstrap, factory,
                    new SimulatorReconnectPolicy(Duration.ofSeconds(1), Duration.ofSeconds(4)),
                    2, lifecycle, manifest, (topic, payload) -> { },
                    new LocalSimulatorActivationEvidenceVerifier(true, OPERATION_ID,
                            Duration.ofSeconds(30), Clock.fixed(NOW, ZoneOffset.UTC)));
        }

        private SimulatorMqttClient connectedClient() throws Exception {
            SimulatorMqttClient client = client();
            client.acceptBootstrap(grant(), identity, credential());
            client.connect();
            return client;
        }

        private SimulatorMqttClient activeClient() throws Exception {
            SimulatorMqttClient client = connectedClient();
            client.markActivationVerified(new SimulatorActivationEvidence(
                    DEVICE_KEY, OPERATION_ID, NOW, "offline-proof"));
            return client;
        }

        private byte[] command(String id, Object value) {
            return ("{\"commandId\":\"" + id
                    + "\",\"correlationId\":\"corr-1\",\"capabilityKey\":\"power\",\"value\":"
                    + new ObjectMapper().valueToTree(value)
                    + ",\"issuedAt\":\"2026-01-01T00:00:00Z\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        private static CapabilityManifest manifest() {
            CapabilityManifest value = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
            CapabilityDefinition temperature = definition("temperature", CapabilityCategory.MEASUREMENT,
                    CapabilityDataType.NUMBER);
            CapabilityDefinition power = definition("power", CapabilityCategory.CONTROL,
                    CapabilityDataType.BOOLEAN);
            power.setControl(new CapabilityControl(CapabilityControlType.SWITCH));
            value.setCapabilities(List.of(temperature, power));
            return value;
        }

        private static CapabilityDefinition definition(String key, CapabilityCategory category,
                CapabilityDataType type) {
            CapabilityDefinition definition = new CapabilityDefinition();
            definition.setKey(key);
            definition.setName(key);
            definition.setCategory(category);
            definition.setDataType(type);
            return definition;
        }
    }

    private static final class FakeFactory implements SimulatorMqttConnectionFactory {
        private final FakeConnection connection = new FakeConnection();
        private int createCount;

        @Override
        public SimulatorMqttConnection create(SimulatorDeviceIdentity identity,
                SimulatorMqttConfiguration configuration, String username, String clientId,
                char[] password, Consumer<SimulatorMqttFailure> disconnectListener) {
            createCount++;
            assertEquals(identity.clientId(), username);
            assertEquals(identity.clientId(), clientId);
            assertTrue(password.length > 0);
            connection.disconnectListener = disconnectListener;
            return connection;
        }
    }

    private static final class FakeConnection implements SimulatorMqttConnection {
        private Consumer<SimulatorMqttFailure> disconnectListener = failure -> { };
        private SimulatorMqttException connectFailure;
        private String lastTopic;
        private int connectCount;
        private boolean connected;
        private boolean closed;

        @Override public void connect() throws SimulatorMqttException {
            connectCount++;
            if (connectFailure != null) throw connectFailure;
            connected = true;
        }
        @Override public void subscribe(String topic, BiConsumer<String, byte[]> consumer) {
            assertEquals("nexaiot/devices/" + DEVICE_KEY + "/commands", topic);
        }
        @Override public void publish(String topic, byte[] payload, int qos) {
            lastTopic = topic;
        }
        @Override public void disconnect() { connected = false; }
        @Override public boolean isConnected() { return connected; }
        @Override public void close() { closed = true; }
    }
}
