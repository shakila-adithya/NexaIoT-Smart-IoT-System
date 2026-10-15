package com.nexaiot.server.mqtt;

import com.nexaiot.server.sensor.SensorReadingService;
import com.nexaiot.server.sensor.dto.CreateSensorReadingRequest;

import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.json.JsonMapper;

@Component
public class MqttTelemetryHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryHandler.class);

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
            Object topicHeader = message.getHeaders().get("mqtt_receivedTopic");

            String payload = message.getPayload().toString();

            if (!(topicHeader instanceof String topic)) {
                log.warn("Rejected MQTT telemetry message with no valid topic.");
                return;
            }

            String deviceKey = MqttTopicSecurity.parseInboundDeviceKey(topic, "telemetry");

            CreateSensorReadingRequest request =
                jsonMapper.readValue(
                    payload,
                    CreateSensorReadingRequest.class
                );

            sensorReadingService.createReadingFromDeviceKey(
                deviceKey,
                request
            );

            log.debug("MQTT telemetry accepted.");

        } catch (Exception ex) {
            log.warn("Rejected MQTT telemetry message.");
        }
    }
}
