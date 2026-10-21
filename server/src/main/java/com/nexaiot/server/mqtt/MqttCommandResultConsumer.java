package com.nexaiot.server.mqtt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaiot.server.mqtt.MqttTopicSecurity;

/**
 * Validates and consumes the existing device-scoped command-result envelope.
 * It never executes commands or changes device state; status changes go only
 * through the injected command store.
 */
public final class MqttCommandResultConsumer {

    private static final int MAX_PAYLOAD_BYTES = 8 * 1024;

    private final ObjectMapper mapper;
    private final MqttCommandStatusStore store;
    private final Duration maximumAge;
    private final Clock clock;

    public MqttCommandResultConsumer(ObjectMapper mapper, MqttCommandStatusStore store,
            Duration maximumAge) {
        this(mapper, store, maximumAge, Clock.systemUTC());
    }

    MqttCommandResultConsumer(ObjectMapper mapper, MqttCommandStatusStore store,
            Duration maximumAge, Clock clock) {
        if (mapper == null || store == null || maximumAge == null || maximumAge.isZero()
                || maximumAge.isNegative() || clock == null) {
            throw new IllegalArgumentException("Command-result policy is invalid.");
        }
        this.mapper = mapper.copy();
        this.store = store;
        this.maximumAge = maximumAge;
        this.clock = clock;
    }

    public boolean consume(String topic, byte[] payload) {
        try {
            String deviceKey = MqttTopicSecurity.parseInboundDeviceKey(topic, "command-results");
            if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
                return false;
            }
            JsonNode node = mapper.readTree(payload);
            if (node == null || !node.isObject() || !node.path("commandId").isTextual()
                    || node.path("commandId").asText().isBlank()
                    || !node.path("status").isTextual()
                    || !node.path("processedAt").isTextual()) {
                return false;
            }
            String commandId = node.path("commandId").asText();
            String status = node.path("status").asText();
            if (!"ACCEPTED".equals(status) && !"DUPLICATE".equals(status)
                    && !"REJECTED".equals(status)) {
                return false;
            }
            Instant processedAt = Instant.parse(node.path("processedAt").asText());
            Duration age = Duration.between(processedAt, Instant.now(clock));
            if (age.isNegative() || age.compareTo(maximumAge) > 0) {
                return false;
            }
            MqttPendingCommand pending = store.findPending(deviceKey, commandId).orElse(null);
            if (pending == null || !deviceKey.equals(pending.deviceKey())) {
                return false;
            }
            String correlationId = node.path("correlationId").isTextual()
                    ? node.path("correlationId").asText() : null;
            if (pending.correlationId() != null && !pending.correlationId().equals(correlationId)) {
                return false;
            }
            String reason = node.path("reason").isTextual() ? node.path("reason").asText() : null;
            return store.recordResult(new MqttCommandResultStatus(
                    deviceKey, commandId, correlationId, status, reason, processedAt));
        } catch (Exception exception) {
            return false;
        }
    }
}
