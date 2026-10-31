package com.nexaiot.server.mqtt;

import java.time.Instant;

import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityManifestAdapter;

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

            Object rawPayload = message.getPayload();
            String payload = rawPayload instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(rawPayload);

            if (!(topicHeader instanceof String topic)) {
                log.warn("Rejected MQTT state message with no valid topic.");
                return;
            }

            String deviceKey = MqttTopicSecurity.parseInboundDeviceKey(topic, "state");

            Map<String, Object> state = jsonMapper.readValue(
                    payload,
                    new TypeReference<Map<String, Object>>() {}
            );
            if (state == null || state.isEmpty()) return;

            Device device = deviceRepository
                    .findByDeviceKey(deviceKey)
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Device not found"
                            )
                    );

            Instant now = Instant.now();

            var manifest = CapabilityManifestAdapter.effectiveManifest(device);
            for (String key : state.keySet()) {
                boolean declared = manifest.getCapabilities().stream().anyMatch(definition ->
                        key.equals(definition.getKey())
                                && (definition.getCategory() == CapabilityCategory.CONTROL
                                || definition.getCategory() == CapabilityCategory.STATE));
                if (!declared) {
                    log.warn("Rejected MQTT state value for undeclared capability.");
                    return;
                }
            }
            Map<String, Object> reported = device.getReportedStates() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(device.getReportedStates());
            reported.putAll(state);
            device.setReportedStates(reported);
            Object power = state.get("power");
            if (power instanceof Boolean powerValue) device.setPowerOn(powerValue);
            device.setLastSeenAt(now);
            device.setUpdatedAt(now);

            deviceRepository.save(device);

            log.debug("MQTT device state accepted.");

        } catch (Exception ex) {
            log.warn("Rejected MQTT state message.");
        }
    }

}
