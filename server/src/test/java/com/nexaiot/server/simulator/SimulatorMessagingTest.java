package com.nexaiot.server.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityConstraints;
import com.nexaiot.server.device.CapabilityControl;
import com.nexaiot.server.device.CapabilityControlType;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;

class SimulatorMessagingTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SimulatorMessageCodec codec = new SimulatorMessageCodec(mapper);
    private final SimulatorDeviceIdentity identity =
            SimulatorDeviceIdentity.forDeviceKey("NEXA-MSG-01", "hardware-hash");

    @Test
    void telemetryUsesExistingMetricsBatteryRssiAndTimestampContract() throws Exception {
        byte[] payload = codec.telemetry(identity, fullManifest(), new SimulatorTelemetry(
                identity.deviceKey(), Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("temperature", 22.5, "battery", 90, "rssi", -55)));
        JsonNode json = mapper.readTree(payload);
        assertEquals(22.5, json.path("metrics").path("temperature").doubleValue());
        assertEquals(90, json.path("battery").intValue());
        assertEquals(-55, json.path("rssi").intValue());
        assertEquals("2026-01-01T00:00:00Z", json.path("timestamp").asText());
    }

    @Test
    void telemetryRejectsUnsupportedFieldsTypesRangesAndOversizedPayloads() {
        CapabilityManifest manifest = fullManifest();
        assertThrows(IllegalArgumentException.class, () -> codec.telemetry(identity, manifest,
                new SimulatorTelemetry(identity.deviceKey(), Instant.now(), Map.of("power", true))));
        assertThrows(IllegalArgumentException.class, () -> codec.telemetry(identity, manifest,
                new SimulatorTelemetry(identity.deviceKey(), Instant.now(), Map.of("temperature", 101))));
        assertThrows(IllegalArgumentException.class, () -> codec.telemetry(identity, manifest,
                new SimulatorTelemetry(identity.deviceKey(), Instant.now(), Map.of("missing", 1))));
        assertThrows(IllegalArgumentException.class, () -> codec.telemetry(identity, manifest,
                new SimulatorTelemetry(identity.deviceKey(), Instant.now(), Map.of(
                        "temperature", "x"))));
    }

    @Test
    void sensorOnlyAndActuatorStateRemainCapabilityDriven() throws Exception {
        CapabilityManifest sensorOnly = manifest(measurement("temperature", 0, 100));
        assertThrows(IllegalArgumentException.class, () -> codec.state(identity, sensorOnly,
                new SimulatorState(Map.of("temperature", 20))));
        assertThrows(IllegalArgumentException.class, () -> codec.state(identity, sensorOnly,
                new SimulatorState(Map.of("power", true))));

        CapabilityManifest actuator = manifest(control("power", CapabilityDataType.BOOLEAN));
        JsonNode state = mapper.readTree(codec.state(identity, actuator,
                new SimulatorState(Map.of("power", true))));
        assertTrue(state.path("power").asBoolean());
    }

    @Test
    void commandsAreDecodedValidatedAppliedAndDeduplicated() throws Exception {
        SimulatorCommandProcessor processor = new SimulatorCommandProcessor(
                identity, fullManifest(), codec,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        byte[] command = command("cmd-1", "corr-1", "power", true);
        SimulatorCommandResult accepted = processor.process(
                SimulatorMqttTopics.commands(identity), command);
        assertEquals(SimulatorCommandResult.Status.ACCEPTED, accepted.status());
        assertEquals(true, processor.currentState().values().get("power"));
        SimulatorCommandResult duplicate = processor.process(
                SimulatorMqttTopics.commands(identity), command);
        assertEquals(SimulatorCommandResult.Status.DUPLICATE, duplicate.status());
        assertThrows(IllegalArgumentException.class, () -> processor.process(
                "nexaiot/devices/NEXA-OTHER/commands", command));
    }

    @Test
    void malformedUnsupportedAndOutOfRangeCommandsAreRejected() {
        SimulatorCommandProcessor processor = new SimulatorCommandProcessor(
                identity, fullManifest(), codec);
        SimulatorCommandResult malformed = processor.process(
                SimulatorMqttTopics.commands(identity), "not-json".getBytes());
        assertEquals(SimulatorCommandResult.Status.REJECTED, malformed.status());
        SimulatorCommandResult unsupported = processor.process(
                SimulatorMqttTopics.commands(identity), command("cmd-2", null, "missing", true));
        assertEquals(SimulatorCommandResult.Status.REJECTED, unsupported.status());
        SimulatorCommandResult outOfRange = processor.process(
                SimulatorMqttTopics.commands(identity), command("cmd-3", null, "setpoint", 101));
        assertEquals(SimulatorCommandResult.Status.REJECTED, outOfRange.status());
    }

    @Test
    void commandAcknowledgementIsNonSecretAndUsesDeviceScopedResultTopic() throws Exception {
        SimulatorCommandResult result = new SimulatorCommandResult(
                "cmd-1", "corr-1", "power", SimulatorCommandResult.Status.ACCEPTED,
                null, Instant.parse("2026-01-01T00:00:00Z"));
        JsonNode json = mapper.readTree(codec.commandResult(result));
        assertEquals("ACCEPTED", json.path("status").asText());
        assertEquals("nexaiot/devices/NEXA-MSG-01/command-results",
                SimulatorMqttTopics.commandResults(identity));
        assertFalse(new String(codec.commandResult(result)).contains("password"));
    }

    @Test
    void activationEvidenceRequiresFreshBoundAndEnabledVerification() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        LocalSimulatorActivationEvidenceVerifier verifier =
                new LocalSimulatorActivationEvidenceVerifier(true, "operation-1",
                        Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC));
        SimulatorActivationEvidence valid = new SimulatorActivationEvidence(
                identity.deviceKey(), "operation-1", now.minusSeconds(10), "proof-ref");
        assertTrue(verifier.verify(identity, valid));
        assertFalse(verifier.verify(identity, new SimulatorActivationEvidence(
                "NEXA-OTHER", "operation-1", now, "proof-ref")));
        assertFalse(verifier.verify(identity, new SimulatorActivationEvidence(
                identity.deviceKey(), "other-operation", now, "proof-ref")));
        assertFalse(verifier.verify(identity, new SimulatorActivationEvidence(
                identity.deviceKey(), "operation-1", now.minusSeconds(31), "proof-ref")));
        assertFalse(new LocalSimulatorActivationEvidenceVerifier(
                false, "operation-1", Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC))
                .verify(identity, valid));
    }

    private byte[] command(String commandId, String correlationId, String key, Object value) {
        String correlation = correlationId == null ? "" : ",\"correlationId\":\"" + correlationId + "\"";
        return ("{\"commandId\":\"" + commandId + "\"" + correlation
                + ",\"capabilityKey\":\"" + key + "\",\"value\":"
                + mapper.valueToTree(value) + ",\"issuedAt\":\"2026-01-01T00:00:00Z\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private CapabilityManifest fullManifest() {
        CapabilityDefinition setpoint = control("setpoint", CapabilityDataType.NUMBER);
        CapabilityConstraints setpointConstraints = new CapabilityConstraints();
        setpointConstraints.setMin(0d);
        setpointConstraints.setMax(100d);
        setpoint.setConstraints(setpointConstraints);
        return manifest(measurement("temperature", 0, 100),
                measurement("battery", 0, 100),
                measurement("rssi", -120, 0),
                control("power", CapabilityDataType.BOOLEAN),
                setpoint);
    }

    private CapabilityManifest manifest(CapabilityDefinition... definitions) {
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        manifest.setCapabilities(List.of(definitions));
        return manifest;
    }

    private CapabilityDefinition measurement(String key, double min, double max) {
        CapabilityDefinition definition = definition(key, CapabilityCategory.MEASUREMENT,
                CapabilityDataType.NUMBER);
        CapabilityConstraints constraints = new CapabilityConstraints();
        constraints.setMin(min);
        constraints.setMax(max);
        definition.setConstraints(constraints);
        return definition;
    }

    private CapabilityDefinition control(String key, CapabilityDataType type) {
        CapabilityDefinition definition = definition(key, CapabilityCategory.CONTROL, type);
        definition.setControl(new CapabilityControl(CapabilityControlType.SWITCH));
        return definition;
    }

    private CapabilityDefinition definition(
            String key, CapabilityCategory category, CapabilityDataType type) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(key);
        definition.setCategory(category);
        definition.setDataType(type);
        return definition;
    }
}
