package com.nexaiot.server.simulator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

class SimulatorFoundationTest {

    @Test
    void identityAndTopicsAreStableAndDeviceScoped() {
        SimulatorDeviceIdentity identity = SimulatorDeviceIdentity.forDeviceKey(
                "NEXA-SIM-01", "hardware-hash");

        assertEquals("nexaiot-device-NEXA-SIM-01", identity.clientId());
        assertEquals("nexaiot/devices/NEXA-SIM-01/telemetry",
                SimulatorMqttTopics.telemetry(identity));
        assertEquals("nexaiot/devices/NEXA-SIM-01/state",
                SimulatorMqttTopics.state(identity));
        assertEquals("nexaiot/devices/NEXA-SIM-01/commands",
                SimulatorMqttTopics.commands(identity));
        assertThrows(IllegalArgumentException.class,
                () -> SimulatorDeviceIdentity.forDeviceKey("NEXA/OTHER", "hash"));
    }

    @Test
    void bootstrapIsOwnerBoundSingleUseAndRedactsCredentials() {
        SimulatorDeviceIdentity identity = SimulatorDeviceIdentity.forDeviceKey(
                "NEXA-SIM-01", "hardware-hash");
        GeneratedDeviceCredential credential = new GeneratedDeviceCredential(
                identity.clientId(), identity.clientId(), "offline-password");
        LocalSimulatorBootstrapAdapter adapter = new LocalSimulatorBootstrapAdapter(true);
        SimulatorBootstrapGrant grant = adapter.authorize(identity, "Owner@Example.com", "operation-1");

        try (SimulatorCredentialEnvelope envelope = adapter.deliver(grant, identity, credential)) {
            assertEquals("owner@example.com", grant.ownerEmail());
            assertArrayEquals("offline-password".toCharArray(), envelope.passwordCopy());
            assertFalse(envelope.toString().contains("offline-password"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> adapter.deliver(grant, identity, credential));
    }

    @Test
    void disabledBootstrapCannotDeliverCredentials() {
        SimulatorDeviceIdentity identity = SimulatorDeviceIdentity.forDeviceKey("NEXA-SIM-01", "hash");
        LocalSimulatorBootstrapAdapter adapter = new LocalSimulatorBootstrapAdapter(false);
        assertThrows(IllegalStateException.class,
                () -> adapter.authorize(identity, "owner@example.com", "operation-1"));
    }

    @Test
    void lifecycleRequiresDeviceEvidenceBeforeActiveAndHonorsRevocation() {
        SimulatorLifecycle lifecycle = new SimulatorLifecycle();
        lifecycle.pairingStarted();
        lifecycle.pairingClaimed();
        lifecycle.enrollmentAuthorized();
        lifecycle.brokerProvisioned();
        assertThrows(IllegalStateException.class, lifecycle::activationVerified);
        lifecycle.credentialsDelivered();
        lifecycle.connected();
        assertEquals(SimulatorLifecycleState.CONNECTED, lifecycle.state());
        lifecycle.activationVerified();
        lifecycle.revoked();
        assertThrows(IllegalStateException.class, lifecycle::requireConnectable);
    }

    @Test
    void capabilityMessagesAcceptValidValuesAndRejectUnsupportedCommands() {
        CapabilityManifest manifest = manifest();
        SimulatorDeviceIdentity identity = SimulatorDeviceIdentity.forDeviceKey("NEXA-SIM-01", "hash");
        SimulatorCapabilityValidator.validateTelemetry(identity, manifest,
                new SimulatorTelemetry(identity.deviceKey(), Instant.now(), Map.of("temperature", 21.5)));
        SimulatorCapabilityValidator.validateCommand(manifest,
                new SimulatorCommand("power", true, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> SimulatorCapabilityValidator.validateTelemetry(identity, manifest,
                        new SimulatorTelemetry(identity.deviceKey(), Instant.now(), Map.of("power", true))));
        assertThrows(IllegalArgumentException.class,
                () -> SimulatorCapabilityValidator.validateCommand(manifest,
                        new SimulatorCommand("unsupported", true, Map.of())));
    }

    @Test
    void tlsConfigurationAndReconnectBackoffFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatorMqttConfiguration(URI.create("tcp://localhost:1883"),
                        Path.of("ca.crt"), "simulator"));
        SimulatorMqttConfiguration configuration = new SimulatorMqttConfiguration(
                URI.create("ssl://localhost:1885"), Path.of("ca.crt"), "simulator");
        assertEquals("ssl", configuration.brokerUri().getScheme());

        SimulatorReconnectPolicy policy = new SimulatorReconnectPolicy(
                Duration.ofSeconds(1), Duration.ofSeconds(4));
        assertEquals(Duration.ofSeconds(1), policy.delayForAttempt(0));
        assertEquals(Duration.ofSeconds(4), policy.delayForAttempt(5));
    }

    private CapabilityManifest manifest() {
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        CapabilityDefinition temperature = definition(
                "temperature", CapabilityCategory.MEASUREMENT, CapabilityDataType.NUMBER);
        CapabilityDefinition power = definition(
                "power", CapabilityCategory.CONTROL, CapabilityDataType.BOOLEAN);
        power.setReadOnly(false);
        com.nexaiot.server.device.CapabilityControl control =
                new com.nexaiot.server.device.CapabilityControl();
        control.setType(com.nexaiot.server.device.CapabilityControlType.SWITCH);
        power.setControl(control);
        temperature.setOptions(List.of());
        manifest.setCapabilities(List.of(temperature, power));
        return manifest;
    }

    private CapabilityDefinition definition(
            String key, CapabilityCategory category, CapabilityDataType dataType) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(key);
        definition.setCategory(category);
        definition.setDataType(dataType);
        return definition;
    }
}
