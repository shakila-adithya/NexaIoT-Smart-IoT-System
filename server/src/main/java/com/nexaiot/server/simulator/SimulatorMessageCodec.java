package com.nexaiot.server.simulator;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;

/** Serializes the existing NexaIoT telemetry/state shape and simulator commands. */
public final class SimulatorMessageCodec {

    public static final int MAX_PAYLOAD_BYTES = 8 * 1024;

    private final ObjectMapper mapper;

    public SimulatorMessageCodec(ObjectMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("JSON mapper is required.");
        }
        this.mapper = mapper.copy();
    }

    public byte[] telemetry(
            SimulatorDeviceIdentity identity,
            CapabilityManifest manifest,
            SimulatorTelemetry telemetry
    ) {
        SimulatorCapabilityValidator.validateTelemetry(identity, manifest, telemetry);
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode metrics = payload.putObject("metrics");
        for (Map.Entry<String, Object> entry : telemetry.values().entrySet()) {
            CapabilityDefinition definition = definition(manifest, entry.getKey());
            if (definition == null || definition.getDataType() != CapabilityDataType.NUMBER
                    || !(entry.getValue() instanceof Number number)) {
                throw new IllegalArgumentException("Telemetry field is not a numeric measurement.");
            }
            if ("battery".equals(entry.getKey())) {
                payload.put("battery", boundedInteger(number, 0, 100));
            } else if ("rssi".equals(entry.getKey())) {
                payload.put("rssi", boundedInteger(number, Integer.MIN_VALUE, Integer.MAX_VALUE));
            } else {
                metrics.put(entry.getKey(), number.doubleValue());
            }
        }
        payload.put("timestamp", telemetry.capturedAt().toString());
        return bytes(payload);
    }

    public byte[] state(
            SimulatorDeviceIdentity identity,
            CapabilityManifest manifest,
            SimulatorState state
    ) {
        if (identity == null || !com.nexaiot.server.device.CapabilityManifestValidator.isValid(manifest)
                || state == null) {
            throw new IllegalArgumentException("Simulator state is invalid.");
        }
        ObjectNode payload = mapper.createObjectNode();
        for (Map.Entry<String, Object> entry : state.values().entrySet()) {
            CapabilityDefinition definition = definition(manifest, entry.getKey());
            if (definition == null || !isStateCapable(definition)
                    || !matches(definition, entry.getValue())) {
                throw new IllegalArgumentException("Simulator state field is unsupported.");
            }
            payload.set(entry.getKey(), mapper.valueToTree(entry.getValue()));
        }
        if (payload.isEmpty()) {
            throw new IllegalArgumentException("Simulator state cannot be empty.");
        }
        return bytes(payload);
    }

    public SimulatorCommand decodeCommand(byte[] payload) {
        requireSize(payload);
        try {
            JsonNode node = mapper.readTree(payload);
            if (node == null || !node.isObject()
                    || !node.path("commandId").isTextual()
                    || node.path("commandId").asText().isBlank()
                    || !node.path("capabilityKey").isTextual()
                    || node.path("capabilityKey").asText().isBlank()
                    || !node.has("value")) {
                throw new IllegalArgumentException("Command payload schema is invalid.");
            }
            String correlationId = node.path("correlationId").isTextual()
                    ? node.path("correlationId").asText() : null;
            Instant issuedAt = node.path("issuedAt").isTextual()
                    ? Instant.parse(node.path("issuedAt").asText()) : null;
            if (issuedAt == null) {
                throw new IllegalArgumentException("Command timestamp is required.");
            }
            Object value = mapper.treeToValue(node.get("value"), Object.class);
            return new SimulatorCommand(
                    node.path("capabilityKey").asText(), value,
                    Map.of("commandId", node.path("commandId").asText(),
                            "correlationId", correlationId == null ? "" : correlationId,
                            "issuedAt", issuedAt.toString()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Command payload is invalid.", exception);
        }
    }

    public String commandId(SimulatorCommand command) {
        return String.valueOf(command.metadata().get("commandId"));
    }

    public String correlationId(SimulatorCommand command) {
        Object value = command.metadata().get("correlationId");
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    public byte[] commandResult(SimulatorCommandResult result) {
        ObjectNode payload = mapper.createObjectNode();
        if (result.commandId() != null) {
            payload.put("commandId", result.commandId());
        }
        if (result.correlationId() != null) {
            payload.put("correlationId", result.correlationId());
        }
        if (result.capabilityKey() != null) {
            payload.put("capabilityKey", result.capabilityKey());
        }
        payload.put("status", result.status().name());
        if (result.reason() != null) {
            payload.put("reason", result.reason());
        }
        payload.put("processedAt", result.processedAt().toString());
        return bytes(payload);
    }

    private byte[] bytes(JsonNode payload) {
        try {
            byte[] result = mapper.writeValueAsBytes(payload);
            requireSize(result);
            return result;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Simulator payload could not be serialized.", exception);
        }
    }

    private void requireSize(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Simulator MQTT payload size is invalid.");
        }
    }

    private int boundedInteger(Number value, int minimum, int maximum) {
        double number = value.doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < minimum || number > maximum) {
            throw new IllegalArgumentException("Telemetry integer field is invalid.");
        }
        return value.intValue();
    }

    private boolean isStateCapable(CapabilityDefinition definition) {
        return definition.getCategory() == CapabilityCategory.STATE
                || definition.getCategory() == CapabilityCategory.CONTROL;
    }

    private boolean matches(CapabilityDefinition definition, Object value) {
        if (value == null) {
            return false;
        }
        return switch (definition.getDataType()) {
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case STRING, ENUM -> value instanceof CharSequence;
        };
    }

    private CapabilityDefinition definition(CapabilityManifest manifest, String key) {
        return manifest.getCapabilities().stream()
                .filter(candidate -> key.equals(candidate.getKey()))
                .findFirst().orElse(null);
    }
}
