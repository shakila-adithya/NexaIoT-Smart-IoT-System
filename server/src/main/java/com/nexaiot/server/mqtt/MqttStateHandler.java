package com.nexaiot.server.mqtt;

import java.time.Instant;

import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;

import tools.jackson.databind.json.JsonMapper;

@Component
public class MqttStateHandler {

    private final JsonMapper jsonMapper;
    private final DeviceRepository deviceRepository;

    public MqttStateHandler(
            JsonMapper jsonMapper,
            DeviceRepository deviceRepository
    ) {
        this.jsonMapper = jsonMapper;
        this.deviceRepository = deviceRepository;
    }

    @ServiceActivator(inputChannel = "mqttStateInputChannel")
    public void handleMessage(Message<?> message) {
        try {
            String topic = (String) message
                    .getHeaders()
                    .get("mqtt_receivedTopic");

            String payload = message.getPayload().toString();

            if (topic == null || topic.isBlank()) {
                System.err.println(
                        "MQTT state message received without topic."
                );
                return;
            }

            String[] parts = topic.split("/");

            if (parts.length != 4) {
                System.err.println(
                        "Invalid MQTT state topic: " + topic
                );
                return;
            }

            String deviceKey = parts[2];

            DeviceStatePayload state =
                    jsonMapper.readValue(
                            payload,
                            DeviceStatePayload.class
                    );

            if (state.power() == null) {
                System.err.println(
                        "MQTT state message missing power value."
                );
                return;
            }

            Device device = deviceRepository
                    .findByDeviceKey(deviceKey)
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Device not found: " + deviceKey
                            )
                    );

            Instant now = Instant.now();

            device.setPowerOn(state.power());
            device.setLastSeenAt(now);
            device.setUpdatedAt(now);

            deviceRepository.save(device);

            System.out.println(
                    "MQTT device state updated: "
                            + deviceKey
                            + " power="
                            + state.power()
            );

        } catch (Exception ex) {
            System.err.println(
                    "Failed to process MQTT device state: "
                            + ex.getMessage()
            );
        }
    }

    public record DeviceStatePayload(
            Boolean power
    ) {
    }
}