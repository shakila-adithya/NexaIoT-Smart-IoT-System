package com.nexaiot.server.mqtt;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class MqttCommandPublisher {

    private final MessageChannel mqttOutboundChannel;
    private final MqttCommandStatusStore statusStore;
    private final ObjectMapper mapper = new ObjectMapper();

    public MqttCommandPublisher(
            @Qualifier("mqttOutboundChannel")
            MessageChannel mqttOutboundChannel,
            MqttCommandStatusStore statusStore
    ) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.statusStore = statusStore;
    }

    public MqttPendingCommand publishCommand(String deviceKey, String payload) {

        String topic = MqttTopicSecurity.commandTopic(deviceKey);
        ObjectNode command = commandPayload(payload);
        String commandId = command.path("commandId").asText();
        String correlationId = command.path("correlationId").isTextual()
                ? command.path("correlationId").asText() : null;
        String capabilityKey = command.path("capabilityKey").asText();
        MqttPendingCommand pending = new MqttPendingCommand(
                deviceKey, commandId, correlationId, capabilityKey);
        statusStore.registerPending(pending);

        Message<String> message =
                MessageBuilder
                        .withPayload(command.toString())
                        .setHeader(MqttHeaders.TOPIC, topic)
                        .build();

        try {
            if (!mqttOutboundChannel.send(message)) {
                statusStore.cancelPending(pending);
                throw new IllegalStateException("MQTT command channel rejected the message.");
            }
        } catch (RuntimeException exception) {
            statusStore.cancelPending(pending);
            throw exception;
        }
        return pending;
    }

    private ObjectNode commandPayload(String payload) {
        try {
            JsonNode input = mapper.readTree(payload);
            if (input == null || !input.isObject()) {
                throw new IllegalArgumentException("Command payload must be an object.");
            }
            ObjectNode command = (ObjectNode) input;
            if (command.has("commandId") || command.has("capabilityKey")
                    || command.has("value")) {
                if (!command.path("capabilityKey").isTextual()
                        || command.path("capabilityKey").asText().isBlank()
                        || !command.has("value") || command.path("value").isContainerNode()
                        || command.path("value").isNull()) {
                    throw new IllegalArgumentException("Structured command payload is invalid.");
                }
                if (!command.path("commandId").isTextual()
                        || command.path("commandId").asText().isBlank()) {
                    command.put("commandId", UUID.randomUUID().toString());
                    command.put("correlationId", UUID.randomUUID().toString());
                }
                if (!command.path("issuedAt").isTextual()
                        || command.path("issuedAt").asText().isBlank()) {
                    command.put("issuedAt", Instant.now().toString());
                }
                return command;
            }
            if (!command.path("power").isBoolean()) {
                throw new IllegalArgumentException("Legacy power command must be boolean.");
            }
            boolean power = command.path("power").asBoolean();
            String commandId = UUID.randomUUID().toString();
            command.removeAll();
            command.put("commandId", commandId);
            command.put("correlationId", UUID.randomUUID().toString());
            command.put("capabilityKey", "power");
            command.put("value", power);
            command.put("issuedAt", Instant.now().toString());
            return command;
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Command payload is invalid.", exception);
        }
    }
}
