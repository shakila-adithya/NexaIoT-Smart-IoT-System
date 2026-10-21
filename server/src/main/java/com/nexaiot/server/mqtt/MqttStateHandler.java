package com.nexaiot.server.mqtt;

import java.time.Instant;

import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;

import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "mqtt.startup.enabled", havingValue = "true", matchIfMissing = true)
public class MqttStateHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttStateHandler.class);

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
            Object topicHeader = message.getHeaders().get("mqtt_receivedTopic");

            String payload = message.getPayload().toString();

            if (!(topicHeader instanceof String topic)) {
                log.warn("Rejected MQTT state message with no valid topic.");
                return;
            }

            String deviceKey = MqttTopicSecurity.parseInboundDeviceKey(topic, "state");

            DeviceStatePayload state =
                    jsonMapper.readValue(
                            payload,
                            DeviceStatePayload.class
                    );

            if (state.power() == null) {
                log.warn("Rejected MQTT state message without a power value.");
                return;
            }

            Device device = deviceRepository
                    .findByDeviceKey(deviceKey)
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Device not found"
                            )
                    );

            Instant now = Instant.now();

            device.setPowerOn(state.power());
            device.setLastSeenAt(now);
            device.setUpdatedAt(now);

            deviceRepository.save(device);

            log.debug("MQTT device state accepted.");

        } catch (Exception ex) {
            log.warn("Rejected MQTT state message.");
        }
    }

    public record DeviceStatePayload(
            Boolean power
    ) {
    }
}
