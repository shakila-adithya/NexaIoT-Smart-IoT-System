package com.nexaiot.server.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class MqttCommandPublisherTest {

    @Test
    void legacyPowerCommandBecomesCorrelatableStructuredCommand() throws Exception {
        RecordingChannel channel = new RecordingChannel(true);
        RecordingStore store = new RecordingStore();
        MqttPendingCommand pending = new MqttCommandPublisher(channel, store)
                .publishCommand("NEXA-PUBLISH-1", "{\"power\":true}");

        JsonNode command = new ObjectMapper().readTree(channel.message.getPayload().toString());
        assertEquals("nexaiot/devices/NEXA-PUBLISH-1/commands",
                channel.message.getHeaders().get(MqttHeaders.TOPIC));
        assertEquals(pending.commandId(), command.path("commandId").asText());
        assertEquals(pending.correlationId(), command.path("correlationId").asText());
        assertEquals("power", command.path("capabilityKey").asText());
        assertTrue(command.path("value").asBoolean());
        assertTrue(command.path("issuedAt").isTextual());
        assertEquals(pending, store.pending);
    }

    @Test
    void invalidLegacyCommandIsRejectedBeforeRegistration() {
        RecordingStore store = new RecordingStore();
        assertThrows(IllegalArgumentException.class,
                () -> new MqttCommandPublisher(new RecordingChannel(true), store)
                        .publishCommand("NEXA-PUBLISH-1", "{\"power\":1}"));
        assertFalse(store.registered);
    }

    @Test
    void failedOutboundChannelCancelsPendingCommand() {
        RecordingStore store = new RecordingStore();
        assertThrows(IllegalStateException.class,
                () -> new MqttCommandPublisher(new RecordingChannel(false), store)
                        .publishCommand("NEXA-PUBLISH-1", "{\"power\":false}"));
        assertTrue(store.cancelled);
    }

    private static final class RecordingChannel implements MessageChannel {
        private final boolean accepted;
        private Message<?> message;

        private RecordingChannel(boolean accepted) {
            this.accepted = accepted;
        }

        @Override
        public boolean send(Message<?> message) {
            this.message = message;
            return accepted;
        }

        @Override
        public boolean send(Message<?> message, long timeout) {
            return send(message);
        }
    }

    private static final class RecordingStore implements MqttCommandStatusStore {
        private MqttPendingCommand pending;
        private boolean registered;
        private boolean cancelled;

        @Override
        public void registerPending(MqttPendingCommand command) {
            pending = command;
            registered = true;
        }

        @Override
        public void cancelPending(MqttPendingCommand command) {
            cancelled = true;
        }

        @Override
        public Optional<MqttPendingCommand> findPending(String deviceKey, String commandId) {
            return Optional.ofNullable(pending);
        }

        @Override
        public boolean recordResult(MqttCommandResultStatus result) {
            return true;
        }
    }
}
