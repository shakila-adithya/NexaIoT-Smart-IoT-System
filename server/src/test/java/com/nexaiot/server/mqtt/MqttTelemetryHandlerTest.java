package com.nexaiot.server.mqtt;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import com.nexaiot.server.sensor.SensorReadingService;
import com.nexaiot.server.sensor.dto.CreateSensorReadingRequest;
import com.nexaiot.server.sensor.dto.SensorReadingResponse;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttTelemetryHandlerTest {

    @Test
    void malformedTopicIsRejectedBeforePersistenceService() {
        RecordingSensorReadingService service = new RecordingSensorReadingService();
        MqttTelemetryHandler handler = new MqttTelemetryHandler(JsonMapper.builder().build(), service);

        handler.handleMessage(message("nexaiot/devices/NEXA-123/telemetry/extra", "{}"));

        assertFalse(service.called);
    }

    @Test
    void malformedPayloadIsRejectedBeforePersistenceService() {
        RecordingSensorReadingService service = new RecordingSensorReadingService();
        MqttTelemetryHandler handler = new MqttTelemetryHandler(JsonMapper.builder().build(), service);

        handler.handleMessage(message("nexaiot/devices/NEXA-123/telemetry", "not-json"));

        assertFalse(service.called);
    }

    @Test
    void binaryMqttPayloadIsDecodedBeforePersistence() {
        RecordingSensorReadingService service = new RecordingSensorReadingService();
        MqttTelemetryHandler handler = new MqttTelemetryHandler(JsonMapper.builder().build(), service);

        handler.handleMessage(binaryMessage(
                "nexaiot/devices/NEXA-123/telemetry",
                "{\"metrics\":{\"temperature\":24.5}}"));

        assertTrue(service.called);
    }

    private Message<String> message(String topic, String payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("mqtt_receivedTopic", topic)
                .build();
    }

    private Message<byte[]> binaryMessage(String topic, String payload) {
        return MessageBuilder.withPayload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setHeader("mqtt_receivedTopic", topic)
                .build();
    }

    private static final class RecordingSensorReadingService extends SensorReadingService {
        private boolean called;

        private RecordingSensorReadingService() {
            super(null, null, null);
        }

        @Override
        public SensorReadingResponse createReadingFromDeviceKey(
                String deviceKey,
                CreateSensorReadingRequest request
        ) {
            called = true;
            return null;
        }
    }
}
