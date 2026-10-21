package com.nexaiot.server.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.device.CapabilityControl;
import com.nexaiot.server.device.CapabilityControlType;
import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

class SimulatorMqttClientTest {

    @Test
    void doesNotConnectUntilExplicitBootstrapAndConnect() throws Exception {
        TestRig rig = new TestRig();
        try (SimulatorMqttClient client = rig.client()) {
            assertEquals(SimulatorConnectionState.BOOTSTRAP_PENDING, client.state());
            assertEquals(0, rig.factory.createCount);
            client.acceptBootstrap(rig.grant(), rig.identity,
                    new GeneratedDeviceCredential(rig.identity.clientId(), rig.identity.clientId(), "pw"));
            assertEquals(SimulatorConnectionState.CREDENTIALS_AVAILABLE, client.state());
            assertEquals(0, rig.factory.createCount);
            client.connect();
            assertEquals(SimulatorConnectionState.ACTIVATION_PENDING, client.state());
            assertEquals(SimulatorMqttTopics.commands(rig.identity), rig.connection.subscribedTopic);
        }
    }

    @Test
    void connectionRequiresSeparateActivationEvidence() throws Exception {
        TestRig rig = new TestRig();
        try (SimulatorMqttClient client = rig.connectedClient()) {
            assertEquals(SimulatorConnectionState.ACTIVATION_PENDING, client.state());
            assertThrows(SimulatorMqttException.class, () -> client.publishState(new byte[] {1}, 1));
            client.markActivationVerified(new SimulatorActivationEvidence(
                    rig.identity.deviceKey(), "operation-1", Instant.now(), "offline-proof"));
            assertEquals(SimulatorConnectionState.ACTIVE, client.state());
            client.publishTelemetry(new SimulatorTelemetry(
                    rig.identity.deviceKey(), Instant.now(), Map.of("temperature", 22)),
                    new byte[] {2}, 1);
            assertEquals(SimulatorMqttTopics.telemetry(rig.identity), rig.connection.publishedTopic);
        }
    }

    @Test
    void reportsCredentialAndTlsFailuresWithoutExposingSecrets() throws Exception {
        TestRig authFailure = new TestRig();
        authFailure.connection.connectFailure = new SimulatorMqttException(
                SimulatorMqttFailure.INVALID_CREDENTIALS, "rejected");
        try (SimulatorMqttClient client = authFailure.client()) {
            client.acceptBootstrap(authFailure.grant(), authFailure.identity,
                    new GeneratedDeviceCredential(authFailure.identity.clientId(),
                            authFailure.identity.clientId(), "secret-password"));
            assertThrows(SimulatorMqttException.class, client::connect);
            assertEquals(SimulatorMqttFailure.INVALID_CREDENTIALS, client.lastFailure());
            assertFalse(client.toString().contains("secret-password"));
        }

        TestRig tlsFailure = new TestRig();
        tlsFailure.connection.connectFailure = new SimulatorMqttException(
                SimulatorMqttFailure.TLS_VALIDATION_FAILURE, "certificate rejected");
        try (SimulatorMqttClient client = tlsFailure.client()) {
            client.acceptBootstrap(tlsFailure.grant(), tlsFailure.identity,
                    new GeneratedDeviceCredential(tlsFailure.identity.clientId(),
                            tlsFailure.identity.clientId(), "another-secret"));
            assertThrows(SimulatorMqttException.class, client::connect);
            assertEquals(SimulatorMqttFailure.TLS_VALIDATION_FAILURE, client.lastFailure());
        }
    }

    @Test
    void reconnectsWithBoundedBackoffAndStopsAtRetryLimit() throws Exception {
        TestRig rig = new TestRig();
        try (SimulatorMqttClient client = rig.connectedClient()) {
            rig.connection.disconnectListener.accept(SimulatorMqttFailure.UNEXPECTED_DISCONNECT);
            assertEquals(SimulatorConnectionState.DISCONNECTED, client.state());
            assertEquals(Duration.ofSeconds(1), client.nextReconnectDelay());
            rig.connection.connectFailure = new SimulatorMqttException(
                    SimulatorMqttFailure.BROKER_UNAVAILABLE, "offline");
            assertThrows(SimulatorMqttException.class, client::reconnect);
            assertThrows(SimulatorMqttException.class, client::reconnect);
            assertThrows(SimulatorMqttException.class, client::reconnect);
            assertEquals(SimulatorMqttFailure.RETRY_EXHAUSTED, client.lastFailure());
        }
    }

    @Test
    void revocationAndShutdownCloseTheTransportAndBlockReuse() throws Exception {
        TestRig revoked = new TestRig();
        SimulatorMqttClient revokedClient = revoked.connectedClient();
        revokedClient.revokeCredentials();
        assertEquals(SimulatorConnectionState.REVOKED, revokedClient.state());
        assertTrue(revoked.connection.disconnected);
        assertThrows(IllegalStateException.class, revokedClient::connect);
        revokedClient.close();

        TestRig shutdown = new TestRig();
        SimulatorMqttClient shutdownClient = shutdown.connectedClient();
        shutdownClient.shutdown();
        assertEquals(SimulatorConnectionState.STOPPED, shutdownClient.state());
        assertTrue(shutdown.connection.closed);
        shutdownClient.close();
    }

    @Test
    void publicationFailureIsReturnedWithoutRetryingUnboundedly() throws Exception {
        TestRig rig = new TestRig();
        try (SimulatorMqttClient client = rig.connectedClient()) {
            client.markActivationVerified(new SimulatorActivationEvidence(
                    rig.identity.deviceKey(), "operation-1", Instant.now(), "offline-proof"));
            rig.connection.publishFailure = new SimulatorMqttException(
                    SimulatorMqttFailure.BROKER_UNAVAILABLE, "publish failed");
            assertThrows(SimulatorMqttException.class, () -> client.publishTelemetry(
                    new SimulatorTelemetry(rig.identity.deviceKey(), Instant.now(),
                            Map.of("temperature", 20)), new byte[] {1}, 1));
        }
    }

    @Test
    void connectionFactoryRejectsIdentityAndOnlyUsesTlsConfiguration() throws Exception {
        SimulatorDeviceIdentity identity = SimulatorDeviceIdentity.forDeviceKey("NEXA-SIM-02", "hash");
        SimulatorMqttConfiguration config = new SimulatorMqttConfiguration(
                URI.create("ssl://localhost:1885"), Path.of("missing-ca.crt"), identity.clientId());
        PahoSimulatorMqttConnectionFactory factory = new PahoSimulatorMqttConnectionFactory();
        SimulatorMqttException exception = assertThrows(SimulatorMqttException.class,
                () -> factory.create(identity, config, identity.clientId(), identity.clientId(),
                        "pw".toCharArray(), failure -> { }));
        assertEquals(SimulatorMqttFailure.TLS_VALIDATION_FAILURE, exception.failure());
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatorMqttConfiguration(URI.create("tcp://localhost:1883"),
                        Path.of("ca.crt"), identity.clientId()));
    }

    @Test
    void bootstrapReplayAndWrongIdentityAreRejectedBeforeConnection() throws Exception {
        TestRig rig = new TestRig();
        SimulatorBootstrapGrant grant = rig.grant();
        try (SimulatorMqttClient client = rig.client()) {
            SimulatorDeviceIdentity wrong = SimulatorDeviceIdentity.forDeviceKey("NEXA-WRONG", "hash");
            assertThrows(IllegalArgumentException.class, () -> client.acceptBootstrap(grant, wrong,
                    new GeneratedDeviceCredential(wrong.clientId(), wrong.clientId(), "pw")));
            client.acceptBootstrap(grant, rig.identity,
                    new GeneratedDeviceCredential(rig.identity.clientId(), rig.identity.clientId(), "pw"));
            assertThrows(IllegalStateException.class, () -> client.acceptBootstrap(grant, rig.identity,
                    new GeneratedDeviceCredential(rig.identity.clientId(), rig.identity.clientId(), "pw")));
        }
    }

    @Test
    void expiredBootstrapIsRejectedWithoutCreatingAConnection() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        LocalSimulatorBootstrapAdapter issuer = new LocalSimulatorBootstrapAdapter(
                true, Duration.ofSeconds(1), Clock.fixed(now, java.time.ZoneOffset.UTC));
        LocalSimulatorBootstrapAdapter receiver = new LocalSimulatorBootstrapAdapter(
                true, Duration.ofSeconds(1), Clock.fixed(now.plusSeconds(2), java.time.ZoneOffset.UTC));
        SimulatorDeviceIdentity identity = SimulatorDeviceIdentity.forDeviceKey("NEXA-SIM-03", "hash");
        SimulatorBootstrapGrant grant = issuer.authorize(identity, "owner@example.com", "operation-1");

        assertThrows(IllegalArgumentException.class, () -> receiver.deliver(grant, identity,
                new GeneratedDeviceCredential(identity.clientId(), identity.clientId(), "pw")));
    }

    private static final class TestRig {
        private final SimulatorDeviceIdentity identity = SimulatorDeviceIdentity.forDeviceKey(
                "NEXA-SIM-01", "hardware-hash");
        private final SimulatorMqttConfiguration configuration = new SimulatorMqttConfiguration(
                URI.create("ssl://127.0.0.1:1885"), Path.of("ca.crt"), identity.clientId());
        private final LocalSimulatorBootstrapAdapter bootstrap = new LocalSimulatorBootstrapAdapter(true);
        private final FakeFactory factory = new FakeFactory();
        private final FakeConnection connection = factory.connection;
        private final CapabilityManifest manifest = manifest();

        private SimulatorBootstrapGrant grant() {
            return bootstrap.authorize(identity, "owner@example.com", "operation-1");
        }

        private SimulatorMqttClient client() {
            return new SimulatorMqttClient(identity, configuration, bootstrap, factory,
                    new SimulatorReconnectPolicy(Duration.ofSeconds(1), Duration.ofSeconds(4)),
                    2, lifecycle(), manifest, (topic, payload) -> { },
                    new LocalSimulatorActivationEvidenceVerifier(
                            true, "operation-1", Duration.ofMinutes(1)));
        }

        private SimulatorMqttClient connectedClient() throws Exception {
            SimulatorMqttClient client = client();
            client.acceptBootstrap(grant(), identity,
                    new GeneratedDeviceCredential(identity.clientId(), identity.clientId(), "pw"));
            client.connect();
            return client;
        }

        private SimulatorLifecycle lifecycle() {
            SimulatorLifecycle value = new SimulatorLifecycle();
            value.pairingStarted();
            value.pairingClaimed();
            value.enrollmentAuthorized();
            value.brokerProvisioned();
            return value;
        }

        private static CapabilityManifest manifest() {
            CapabilityManifest value = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
            CapabilityDefinition temperature = new CapabilityDefinition();
            temperature.setKey("temperature");
            temperature.setName("Temperature");
            temperature.setCategory(CapabilityCategory.MEASUREMENT);
            temperature.setDataType(CapabilityDataType.NUMBER);
            CapabilityDefinition power = new CapabilityDefinition();
            power.setKey("power");
            power.setName("Power");
            power.setCategory(CapabilityCategory.CONTROL);
            power.setDataType(CapabilityDataType.BOOLEAN);
            power.setControl(new CapabilityControl(CapabilityControlType.SWITCH));
            value.setCapabilities(List.of(temperature, power));
            return value;
        }
    }

    private static final class FakeFactory implements SimulatorMqttConnectionFactory {
        private final FakeConnection connection = new FakeConnection();
        private int createCount;

        @Override
        public SimulatorMqttConnection create(
                SimulatorDeviceIdentity identity,
                SimulatorMqttConfiguration configuration,
                String username,
                String clientId,
                char[] password,
                Consumer<SimulatorMqttFailure> disconnectListener
        ) {
            createCount++;
            connection.disconnectListener = disconnectListener;
            assertEquals(identity.clientId(), username);
            assertEquals(identity.clientId(), clientId);
            assertTrue(password.length > 0);
            return connection;
        }
    }

    private static final class FakeConnection implements SimulatorMqttConnection {
        private SimulatorMqttException connectFailure;
        private Consumer<SimulatorMqttFailure> disconnectListener = failure -> { };
        private String subscribedTopic;
        private String publishedTopic;
        private boolean connected;
        private boolean disconnected;
        private boolean closed;
        private SimulatorMqttException publishFailure;

        @Override
        public void connect() throws SimulatorMqttException {
            if (connectFailure != null) {
                throw connectFailure;
            }
            connected = true;
        }

        @Override
        public void subscribe(String exactTopic, BiConsumer<String, byte[]> messageConsumer) {
            subscribedTopic = exactTopic;
            assertFalse(exactTopic.contains("#"));
            assertFalse(exactTopic.contains("+"));
        }

        @Override
        public void publish(String exactTopic, byte[] payload, int qos)
                throws SimulatorMqttException {
            if (publishFailure != null) {
                throw publishFailure;
            }
            publishedTopic = exactTopic;
            assertFalse(exactTopic.contains("#"));
            assertFalse(exactTopic.contains("+"));
        }

        @Override
        public void disconnect() {
            connected = false;
            disconnected = true;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
