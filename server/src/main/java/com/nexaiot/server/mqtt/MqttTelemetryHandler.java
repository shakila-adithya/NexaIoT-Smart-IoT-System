package com.nexaiot.server.mqtt;

import com.nexaiot.server.sensor.SensorReadingService;
import com.nexaiot.server.sensor.dto.CreateSensorReadingRequest;

import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class MqttTelemetryHandler {

    private final JsonMapper jsonMapper;
    private final SensorReadingService sensorReadingService;

    public MqttTelemetryHandler(
        JsonMapper jsonMapper,
        SensorReadingService sensorReadingService
    ) {
        this.jsonMapper = jsonMapper;
        this.sensorReadingService = sensorReadingService;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        try {
            String topic = (String) message
                .getHeaders()
                .get("mqtt_receivedTopic");

            String payload = message.getPayload().toString();

            if (topic == null || topic.isBlank()) {
                System.err.println(
                    "MQTT message received without topic."
                );
                return;
            }

            String[] parts = topic.split("/");

            if (parts.length != 4) {
                System.err.println(
                    "Invalid MQTT topic: " + topic
                );
                return;
            }

            String deviceKey = parts[2];

            CreateSensorReadingRequest request =
                jsonMapper.readValue(
                    payload,
                    CreateSensorReadingRequest.class
                );

            sensorReadingService.createReadingFromDeviceKey(
                deviceKey,
                request
            );

            System.out.println(
                "MQTT telemetry saved for device: "
                    + deviceKey
            );

        } catch (Exception ex) {
            System.err.println(
                "Failed to process MQTT telemetry: "
                    + ex.getMessage()
            );
        }
    }
}